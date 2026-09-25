package one.xis.seabattle.game;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import java.util.ArrayList;
import java.util.List;

/** World-space instrument contours sampled once from the procedural terrain. */
record InstrumentMap(int version, List<Layer> layers) {
    private static final GeometryFactory GEOMETRY = new GeometryFactory();
    record Layer(double height, List<List<Point2>> contours) {}

    static InstrumentMap prepare(WorldMap world) {
        var layers = new ArrayList<Layer>();
        for (double level : new double[]{0, 50, 150}) {
            var islands = new ArrayList<Geometry>();
            for (Landmass land : world.landmasses()) islands.add(areaAbove(land, level));
            layers.add(new Layer(level, contours(UnaryUnionOp.union(islands, GEOMETRY))));
        }
        return new InstrumentMap(1, List.copyOf(layers));
    }

    private static Geometry areaAbove(Landmass land, double level) {
        double cell = Math.max(0.25, Math.min(8, Math.min(land.rx(), land.rz()) / 32));
        double minX = land.x() - land.rx() * 1.6, maxX = land.x() + land.rx() * 1.6;
        double minZ = land.z() - land.rz() * 1.6, maxZ = land.z() + land.rz() * 1.6;
        WorldMap single = new WorldMap(0, List.of(land));
        var strips = new ArrayList<Geometry>();
        int rows = (int) Math.ceil((maxZ - minZ) / cell);
        int columns = (int) Math.ceil((maxX - minX) / cell);
        // Derive shared edges from the same index, avoiding accumulated rounding gaps.
        for (int row = 0; row < rows; row++) {
            double z = minZ + row * cell;
            int start = -1;
            for (int column = 0; column <= columns; column++) {
                double x = minX + column * cell;
                Vector2 position = new Vector2(x + cell / 2, z + cell / 2);
                boolean blocked = column < columns && (level == 0
                        ? LandGeometry.isBlockedByLandmass(position, land)
                        : LandGeometry.terrainHeightAt(position, single) >= level);
                if (blocked && start < 0) start = column;
                if (!blocked && start >= 0) {
                    strips.add(GEOMETRY.toGeometry(new Envelope(minX + start * cell, x,
                            z, minZ + (row + 1) * cell)));
                    start = -1;
                }
            }
        }
        return UnaryUnionOp.union(strips, GEOMETRY);
    }

    private static List<List<Point2>> contours(Geometry area) {
        area.normalize();
        var result = new ArrayList<List<Point2>>();
        for (int i = 0; i < area.getNumGeometries(); i++) {
            if (!(area.getGeometryN(i) instanceof Polygon polygon)) continue;
            result.add(ring(polygon.getExteriorRing().getCoordinates()));
            for (int hole = 0; hole < polygon.getNumInteriorRing(); hole++) {
                result.add(ring(polygon.getInteriorRingN(hole).getCoordinates()));
            }
        }
        return List.copyOf(result);
    }

    private static List<Point2> ring(Coordinate[] coordinates) {
        var points = new ArrayList<Point2>();
        for (int i = 0; i < coordinates.length - 1; i++) {
            points.add(new Point2(coordinates[i].x, coordinates[i].y));
        }
        return List.copyOf(points);
    }
}
