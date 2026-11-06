package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.List;

/** Reusable, static water samples for one landscape. Contains no vehicle state. */
public final class WaterPointGrid {
    public static final double SPACING = 3 * BotNavigation.SHIP_LENGTH;
    private static final double CLEARANCE = 1.5 * BotNavigation.SHIP_LENGTH;
    private record Key(List<Landmass> land, List<Vector2> anchors) { }
    private static final java.util.Map<Key, WaterPointGrid> CACHE = new java.util.LinkedHashMap<>();
    private final List<Vector2> points;
    private final List<Vector2> spawnPoints;

    private WaterPointGrid(List<Vector2> points, List<Vector2> anchors) {
        this.points = List.copyOf(points);
        this.spawnPoints = selectSpawnPoints(this.points, anchors);
    }
    public List<Vector2> points() { return points; }
    public List<Vector2> spawnPoints() { return spawnPoints; }

    private static List<Vector2> selectSpawnPoints(List<Vector2> points, List<Vector2> anchors) {
        if (anchors.isEmpty()) return List.of(); // No defined play area: never fall back to the whole sea.
        double minX = anchors.stream().mapToDouble(Vector2::x).min().orElseThrow();
        double maxX = anchors.stream().mapToDouble(Vector2::x).max().orElseThrow();
        double minZ = anchors.stream().mapToDouble(Vector2::z).min().orElseThrow();
        double maxZ = anchors.stream().mapToDouble(Vector2::z).max().orElseThrow();
        // A lone/collinear anchor still needs room for spaced hulls on small custom maps.
        double halfX = Math.max((maxX - minX) / 2, 6 * SPACING);
        double halfZ = Math.max((maxZ - minZ) / 2, 6 * SPACING);
        double centerX = (minX + maxX) / 2, centerZ = (minZ + maxZ) / 2;
        return points.stream().filter(p -> Math.abs(p.x() - centerX) <= halfX
                && Math.abs(p.z() - centerZ) <= halfZ).toList();
    }

    static synchronized WaterPointGrid cached(WorldMap world, List<Vector2> existingPoints) {
        var key = new Key(List.copyOf(world.landmasses()), List.copyOf(existingPoints));
        WaterPointGrid grid = CACHE.get(key);
        if (grid == null) {
            grid = scan(world, existingPoints);
            if (CACHE.size() >= 4) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(key, grid);
        }
        return grid;
    }

    static WaterPointGrid scan(WorldMap world, List<Vector2> existingPoints) {
        double minX = -600, maxX = 600, minZ = -600, maxZ = 600;
        for (Landmass land : world.landmasses()) {
            minX = Math.min(minX, land.x() - Math.abs(land.rx()) * 1.6);
            maxX = Math.max(maxX, land.x() + Math.abs(land.rx()) * 1.6);
            minZ = Math.min(minZ, land.z() - Math.abs(land.rz()) * 1.6);
            maxZ = Math.max(maxZ, land.z() + Math.abs(land.rz()) * 1.6);
        }
        for (Vector2 point : existingPoints) {
            minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
            minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
        }
        var points = new ArrayList<Vector2>();
        int columns = (int) Math.ceil((maxX - minX) / SPACING);
        int rows = (int) Math.ceil((maxZ - minZ) / SPACING);
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= columns; column++) {
                var point = new Vector2(minX + column * SPACING, minZ + row * SPACING);
                if (hasClearance(point, world)) points.add(point);
            }
        }
        return new WaterPointGrid(points, existingPoints);
    }

    static boolean hasClearance(Vector2 point, WorldMap world) {
        if (LandGeometry.isBlocked(point, world)) return false;
        for (int direction = 0; direction < 8; direction++) {
            double angle = direction * Math.PI / 4;
            for (int step = 1; step <= 6; step++) {
                double radius = CLEARANCE * step / 6;
                if (LandGeometry.isBlocked(new Vector2(point.x() + Math.sin(angle) * radius,
                        point.z() + Math.cos(angle) * radius), world)) return false;
            }
        }
        return true;
    }
}
