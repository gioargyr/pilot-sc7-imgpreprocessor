package serialProcessingOriginal;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.core.gpf.internal.OperatorImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONWriter;

import com.vividsolutions.jts.algorithm.ConvexHull;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

public class Clustering {
	
	public static void main(String[] args) {
		
		String imgsFilePath = args[0];
		
		long startAll = System.currentTimeMillis();
		
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.loadOperatorSpis();
		MyRead readOp = new MyRead(new File(imgsFilePath), spiRegistry);
		JSONObject jsonSthg;
		try {
			jsonSthg = clusterChanges(readOp.targetProduct);
			//System.out.println(jsonSthg.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		long endAll = System.currentTimeMillis();
		long timeAll = endAll - startAll;
		System.out.println(timeAll);
		
	}
	
	public static JSONObject clusterChanges(Product product) throws IOException {
		PixelPos tlPix = null;
		GeoPos tlGeo = null;
		GeoCoding geoCoding = null;
		String[] bNames = null;
		JSONObject crs = null, crsProp = null;
		JSONArray featureList = null;

		bNames = product.getBandNames();
		Band targetBand = product.getBand(bNames[0]);

		OperatorImage inputImg = (OperatorImage) targetBand.getSourceImage().getImage(0);

		Raster raster = inputImg.getData();

		// threshold, eps, minPTS
		int eps = 2;
		int minPTS = 4;
		List<Set<Point>> Clusters = dbScanClusters(raster, eps, minPTS);

		JSONObject featureCollection = new JSONObject();
		featureCollection.put("name", "Layer - ");
		featureCollection.put("type", "FeatureCollection");
		crs = new JSONObject();
		crs.put("type", "name");
		crsProp = new JSONObject();
		crsProp.put("name", "EPSG:4326");
		crs.put("properties", crsProp);
		featureCollection.put("crs", crs);
		featureList = new JSONArray();

		for (Set<Point> cl : Clusters) {
			JSONObject properties = new JSONObject();

			JSONObject JSONpolygon = new JSONObject();
			JSONpolygon.put("type", "Polygon");

			Polygon polygon = new Polygon();

			List<Double> tmpX = new ArrayList<Double>();
			List<Double> tmpY = new ArrayList<Double>();

			for (Point pi : cl) {
				polygon.addPoint((int) pi.getX(), (int) pi.getY());

				tlPix = new PixelPos((int) pi.getX(), (int) pi.getY());
				geoCoding = targetBand.getGeoCoding();
				tlGeo = geoCoding.getGeoPos(tlPix, null);

				tmpX.add(tlGeo.lon);
				tmpY.add(tlGeo.lat);
			}
			Coordinate[] pts = convertCoordinates(tmpX, tmpY);
			GeometryFactory geomFactory = new GeometryFactory();
			ConvexHull hull = new ConvexHull(pts, geomFactory);
			Geometry hullGeom = hull.getConvexHull();
			System.out.println(hullGeom.toString());

			GeoJSONWriter writer = new GeoJSONWriter();
			GeoJSON json = writer.write(hullGeom);

			JSONObject feature = new JSONObject();
			feature.put("type", "Feature");
			feature.put("geometry", new JSONObject(json.toString()));

			feature.put("properties", properties);
			featureList.put(feature);
		}
		
		featureCollection.put("features", featureList);

		return featureCollection;
	}

	public static List<Set<Point>> dbScanClusters(Raster raster, int eps, int minPts) {
		int width = raster.getWidth();
		int height = raster.getHeight();
		double[] dArray = new double[1];
		boolean[][] isChanging = new boolean[width][height];

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				raster.getPixel(i, j, dArray);
				if (dArray[0] == 1) //original
//				if (dArray[0] > 2 || dArray[0] < -2) //manosthanos
					isChanging[i][j] = true;
			}
		}

		List<Set<Point>> Clusters = new ArrayList<Set<Point>>();
		Set<Point> visited = new HashSet<Point>();
		Set<Point> noise = new HashSet<Point>();
		Set<Point> clusterMember = new HashSet<Point>();
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (!isChanging[i][j])
					continue;
				Point point = new Point(i, j);
				if (visited.contains(point)) {
					continue;
				}
				List<Point> neighbors = getNeighbors(point, isChanging, eps);
				if (neighbors.size() >= minPts) {
					Clusters.add(expandCluster(point, neighbors, isChanging,
							visited, clusterMember, noise, eps, minPts));
				} else {
					visited.add(point);
					noise.add(point);
				}
			}
		}
		

		return Clusters;
	}

	public static Set<Point> expandCluster(Point point, List<Point> neighbors,
										    boolean[][] isChanging, Set<Point> visited,
										    Set<Point> clusterMember, Set<Point> noise, int eps, int minPts) {
		Set<Point> cluster = new HashSet<Point>();

		cluster.add(point);
		visited.add(point);
		clusterMember.add(point);
		List<Point> seeds = new ArrayList<Point>(neighbors);
		int index = 0;

		while (index < seeds.size()) {
			Point current = seeds.get(index);
			// only check non-visited points
			if (!(clusterMember.contains(current))) {
				visited.add(current);
				List<Point> currentNeighbors = getNeighbors(current,
						isChanging, eps);
				if (currentNeighbors.size() >= minPts) {
					seeds = merge(seeds, currentNeighbors);
				}
				noise.remove(current);
				clusterMember.add(current);
				cluster.add(current);
			}
			index++;
		}

		return cluster;
	}

	public static List<Point> getNeighbors(Point point, boolean[][] points, int eps) {
		final List<Point> neighbors = new ArrayList<Point>();
		int px = (int) point.x;
		int py = (int) point.y;
		for (int i = 0; i < points.length; i++) {
			for (int j = 0; j < points[0].length; j++) {
				if (points[i][j]
						&& (Math.sqrt(Math.pow((px - i), 2)
								+ Math.pow((py - j), 2)) <= eps)) {
					Point p = new Point(i, j);
					neighbors.add(p);
				}
			}

		}

		return neighbors;
	}

	public static Coordinate[] convertCoordinates(List<Double> X, List<Double> Y) {
		Coordinate[] ret = new Coordinate[X.size()];
		Iterator<Double> iterator = X.iterator();
		Iterator<Double> iterator1 = Y.iterator();
		int i = 0;

		while (iterator.hasNext()) {
			double tmpX = iterator.next();
			double tmpY = iterator1.next();

			ret[i] = new Coordinate(tmpX, tmpY);
			i++;
		}

		return ret;
	}

	private static List<Point> merge(List<Point> one, List<Point> two) {
		final Set<Point> oneSet = new HashSet<Point>(one);

		for (Point item : two) {
			if (!oneSet.contains(item)) {
				one.add(item);
			}
		}

		return one;
	}

}
