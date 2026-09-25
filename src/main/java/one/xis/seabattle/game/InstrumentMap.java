package one.xis.seabattle.game;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/** World-space instrument contours sampled once from the procedural terrain. */
record InstrumentMap(int version, List<Layer> layers) {
    record Layer(double height, List<List<Point2>> contours) {}

    static InstrumentMap prepare(WorldMap world) {
        var layers = new ArrayList<Layer>();
        for (double level : new double[]{0, 50, 150}) {
            Area combined = new Area();
            for (Landmass land : world.landmasses()) combined.add(areaAbove(land, level));
            layers.add(new Layer(level, contours(combined)));
        }
        return new InstrumentMap(1, List.copyOf(layers));
    }

    private static Area areaAbove(Landmass land, double level) {
        double cell = Math.max(0.25, Math.min(8, Math.min(land.rx(), land.rz()) / 32));
        double minX = land.x() - land.rx() * 1.6, maxX = land.x() + land.rx() * 1.6;
        double minZ = land.z() - land.rz() * 1.6, maxZ = land.z() + land.rz() * 1.6;
        WorldMap single = new WorldMap(0, List.of(land));
        Path2D.Double path = new Path2D.Double();
        for (double z = minZ; z < maxZ; z += cell) {
            double start = Double.NaN;
            for (double x = minX; x <= maxX + cell; x += cell) {
                Vector2 position = new Vector2(x + cell / 2, z + cell / 2);
                boolean blocked = x < maxX && (level == 0
                        ? LandGeometry.isBlockedByLandmass(position, land)
                        : LandGeometry.terrainHeightAt(position, single) >= level);
                if (blocked && Double.isNaN(start)) start = x;
                if (!blocked && !Double.isNaN(start)) {
                    path.moveTo(start, z); path.lineTo(x, z);
                    path.lineTo(x, z + cell); path.lineTo(start, z + cell); path.closePath();
                    start = Double.NaN;
                }
            }
        }
        return new Area(path);
    }

    private static List<List<Point2>> contours(Area area) {
        var result = new ArrayList<List<Point2>>();
        List<Point2> ring = null;
        double[] point = new double[6];
        for (var iterator = area.getPathIterator(null, 0.25); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(point);
            if (type == PathIterator.SEG_MOVETO) { ring = new ArrayList<>(); result.add(ring); }
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) ring.add(new Point2(point[0], point[1]));
        }
        return result.stream().filter(points -> points.size() >= 3).map(List::copyOf).toList();
    }
}
