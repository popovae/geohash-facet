package nl.trifork.elasticsearch.facet.geohash;

import java.io.IOException;
import java.util.Arrays;

import org.elasticsearch.common.Preconditions;
import org.elasticsearch.common.geo.GeoHashUtils;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Modified from the original on https://github.com/zenobase/geocluster-facet/blob/master/src/main/java/com/zenobase/search/facet/geocluster/GeoCluster.java
 */
public class Cluster {

    private int geohashBits;

    private int size;
	private GeoPoint center;
    private long clusterGeohash;
	private BoundingBox bounds;

    /**
     * @param clusterGeohash - geohash of the cluster, obtained from {@link nl.trifork.elasticsearch.facet.geohash.BinaryGeoHashUtils#encodeAsLong(org.elasticsearch.common.geo.GeoPoint, int)}
     * @param geohashBits - number of meaningful bits of the geohash. Values: 0 to {@link nl.trifork.elasticsearch.facet.geohash.BinaryGeoHashUtils#MAX_PREFIX_LENGTH}
     */
	public Cluster(GeoPoint point, long clusterGeohash, int geohashBits) {
        this(1, point, clusterGeohash, geohashBits, new BoundingBox(point));

	}

    /**
     * @param clusterGeohash - geohash of the cluster, obtained from {@link nl.trifork.elasticsearch.facet.geohash.BinaryGeoHashUtils#encodeAsLong(org.elasticsearch.common.geo.GeoPoint, int)}
     * @param geohashBits - number of meaningful bits of the geohash. Values: 0 to {@link nl.trifork.elasticsearch.facet.geohash.BinaryGeoHashUtils#MAX_PREFIX_LENGTH}
     */
	public Cluster(int size, GeoPoint center, long clusterGeohash, int geohashBits, BoundingBox bounds) {
        Preconditions.checkArgument(clusterGeohash == BinaryGeoHashUtils.encodeAsLong(center, geohashBits));

		this.size = size;
		this.center = center;
        this.clusterGeohash = clusterGeohash;
        this.geohashBits = geohashBits;
		this.bounds = bounds;
	}

	public void add(GeoPoint point) {
        Preconditions.checkArgument(clusterGeohash == BinaryGeoHashUtils.encodeAsLong(point, geohashBits));

		++size;
		center = mean(center, size - 1, point, 1);
		bounds = bounds.extend(point);
	}

	public Cluster merge(Cluster that) {
        Preconditions.checkArgument(clusterGeohash == that.clusterGeohash &&
            geohashBits == that.geohashBits);

		GeoPoint center = mean(this.center, this.size(), that.center(), that.size());
		return new Cluster(this.size + that.size(),
                center, this.clusterGeohash, this.geohashBits, this.bounds.extend(that.bounds()));
	}

	private static GeoPoint mean(GeoPoint left, int leftWeight, GeoPoint right, int rightWeight) {
		double lat = (left.getLat() * leftWeight + right.getLat() * rightWeight) / (leftWeight + rightWeight);
		double lon = (left.getLon() * leftWeight + right.getLon() * rightWeight) / (leftWeight + rightWeight);
		return new GeoPoint(lat, lon);
	}

	public int size() {
		return size;
	}

	public GeoPoint center() {
		return center;
	}

	public BoundingBox bounds() {
		return bounds;
	}

    public long clusterGeohash() {
        return clusterGeohash;
    }

    public int clusterGeohashBits() {
        return geohashBits;
    }

    public static Cluster readFrom(StreamInput in) throws IOException {
		int size = in.readVInt();
		GeoPoint center = GeoPoints.readFrom(in);
        long clusterGeohash = in.readLong();
        int geohashBits = in.readInt();
		BoundingBox bounds = size > 1
			? BoundingBox.readFrom(in)
			: new BoundingBox(center, center);
		return new Cluster(size, center, clusterGeohash, geohashBits, bounds);
	}

	public void writeTo(StreamOutput out) throws IOException {
		out.writeVInt(size);
		GeoPoints.writeTo(center, out);
        out.writeLong(clusterGeohash);
        out.writeInt(geohashBits);
		if (size > 1) {
			bounds.writeTo(out);
		}
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Cluster &&
			equals((Cluster) that);
	}

	private boolean equals(Cluster that) {
		return size == that.size() &&
			GeoPoints.equals(center, that.center()) &&
			bounds.equals(that.bounds()) &&
            clusterGeohash == that.clusterGeohash &&
            geohashBits == that.geohashBits;
	}

	@Override
	public int hashCode() {
		return hashCode(size, center.toString(), clusterGeohash, bounds);
	}

	private static int hashCode(Object... objects) {
		return Arrays.hashCode(objects);
	}

	@Override
	public String toString() {
		return String.format("%s %s (%d)", GeoPoints.toString(center), clusterGeohash, size);
	}
}
