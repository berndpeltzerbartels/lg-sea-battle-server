package one.xis.seabattle.game;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/** Static world-space contours, prepared once and persisted with imported landscapes. */
record InstrumentMap(int version, List<Layer> layers) {
    static final int VERSION = 1;
    record Layer(double height, List<List<Point2>> contours) {}

    static InstrumentMap prepare(WorldMap world) {
        var layers = new ArrayList<Layer>();
        for (double level : new double[]{0, 50, 150}) {
            Area combined = new Area();
            for (Landmass land : world.landmasses()) combined.add(areaAbove(land, level));
            layers.add(new Layer(level, contours(combined)));
        }
        return new InstrumentMap(VERSION, List.copyOf(layers));
    }

    private static Area areaAbove(Landmass land, double level) {
        if (land.polygon().size() < 3) return builtInArea(land, level);
        Area result = land.baseHeight() >= level ? polygon(land.polygon()) : new Area();
        if (MappedPlateauGeometry.complete(land)) {
            for (var triangle : MappedPlateauGeometry.triangles(land)) {
                var clipped = new ArrayList<Point2>();
                for (int i = 0; i < triangle.size(); i++) {
                    var a = triangle.get(i);
                    var b = triangle.get((i + 1) % triangle.size());
                    if (a.h() >= level) clipped.add(new Point2(a.x(), a.z()));
                    if ((a.h() >= level) != (b.h() >= level)) {
                        double t = (level - a.h()) / (b.h() - a.h());
                        clipped.add(new Point2(a.x() + t * (b.x() - a.x()), a.z() + t * (b.z() - a.z())));
                    }
                }
                result.add(polygon(clipped));
            }
        }
        for (HeightPoint peak : land.heightPoints()) {
            if (peak.plateauGroupId() == null) result.add(polygon(LandGeometry.peakContour(land, peak, level)));
        }
        result.intersect(polygon(land.polygon()));
        for (Lake lake : land.lakes()) {
            result.subtract(new Area(new java.awt.geom.Ellipse2D.Double(
                    land.x() + lake.x() - lake.rx(), land.z() + lake.z() - lake.rz(), 2 * lake.rx(), 2 * lake.rz())));
        }
        for (Waterway waterway : land.waterways()) {
            var segment = new java.awt.geom.Line2D.Double(land.x() + waterway.from().x(), land.z() + waterway.from().z(),
                    land.x() + waterway.to().x(), land.z() + waterway.to().z());
            result.subtract(new Area(new java.awt.BasicStroke((float) (waterway.width() * 1.16),
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND).createStrokedShape(segment)));
        }
        return result;
    }

    // Procedural built-in maps have no authored polygons. Sample their existing
    // collision predicate at startup; imported maps use the exact contours above.
    private static Area builtInArea(Landmass land, double level) {
        double cell = Math.max(0.25, Math.min(16, Math.min(land.rx(), land.rz()) / 16));
        double minX = land.x() - land.rx() * 1.2, maxX = land.x() + land.rx() * 1.2;
        double minZ = land.z() - land.rz() * 1.2, maxZ = land.z() + land.rz() * 1.2;
        WorldMap single = new WorldMap(0, List.of(land));
        Path2D.Double path = new Path2D.Double();
        for (double z = minZ; z < maxZ; z += cell) {
            double start = Double.NaN;
            for (double x = minX; x <= maxX + cell; x += cell) {
                boolean blocked = x < maxX && LandGeometry.isBlockedAtOrAbove(new Vector2(x + cell / 2, z + cell / 2), single, level);
                if (blocked && Double.isNaN(start)) start = x;
                if (!blocked && !Double.isNaN(start)) {
                    path.moveTo(start, z); path.lineTo(x, z); path.lineTo(x, z + cell); path.lineTo(start, z + cell); path.closePath();
                    start = Double.NaN;
                }
            }
        }
        return new Area(path);
    }

    private static Area polygon(List<Point2> points) {
        if (points.size() < 3) return new Area();
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points.get(0).x(), points.get(0).z());
        for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x(), points.get(i).z());
        path.closePath();
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
        return result.stream().filter(ringPoints -> ringPoints.size() >= 3).map(List::copyOf).toList();
    }
}
