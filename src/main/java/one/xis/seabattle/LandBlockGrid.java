package one.xis.seabattle;

import java.util.BitSet;

final class LandBlockGrid {

    private static final double GRID_PADDING = 120.0;

    private final double minX;
    private final double minZ;
    private final double resolution;
    private final int width;
    private final int height;
    private final BitSet radarBlocked;

    private LandBlockGrid(double minX, double minZ, double resolution, int width, int height,
                          BitSet radarBlocked) {
        this.minX = minX;
        this.minZ = minZ;
        this.resolution = resolution;
        this.width = width;
        this.height = height;
        this.radarBlocked = radarBlocked;
    }

    static LandBlockGrid from(WorldMap worldMap, double resolution) {
        Bounds bounds = boundsFor(worldMap, resolution);
        int width = Math.max(1, (int) Math.ceil((bounds.maxX() - bounds.minX()) / resolution) + 1);
        int height = Math.max(1, (int) Math.ceil((bounds.maxZ() - bounds.minZ()) / resolution) + 1);
        BitSet radarBlocked = new BitSet(width * height);
        LandBlockGrid grid = new LandBlockGrid(bounds.minX(), bounds.minZ(), resolution, width, height, radarBlocked);
        worldMap.landmasses().forEach(grid::rasterize);
        return grid;
    }

    boolean isBlocked(Vector2 position) {
        int x = cellX(position.x());
        int z = cellZ(position.z());
        if (x < 0 || z < 0 || x >= width || z >= height) {
            return false;
        }
        int index = index(x, z);
        return radarBlocked.get(index);
    }

    private void rasterize(Landmass landmass) {
        double radarDistance = LandGeometry.radarBlockDistance(landmass);
        int minCellX = Math.max(0, cellX(landmass.x() - landmass.rx() * radarDistance - resolution));
        int maxCellX = Math.min(width - 1, cellX(landmass.x() + landmass.rx() * radarDistance + resolution));
        int minCellZ = Math.max(0, cellZ(landmass.z() - landmass.rz() * radarDistance - resolution));
        int maxCellZ = Math.min(height - 1, cellZ(landmass.z() + landmass.rz() * radarDistance + resolution));

        for (int z = minCellZ; z <= maxCellZ; z += 1) {
            double worldZ = minZ + z * resolution;
            for (int x = minCellX; x <= maxCellX; x += 1) {
                double worldX = minX + x * resolution;
                Vector2 position = new Vector2(worldX, worldZ);
                double distance = LandGeometry.shapeDistance(position, landmass);
                boolean landWater = LandGeometry.isInLandWater(position, landmass);
                int index = index(x, z);
                if (!landWater && distance < radarDistance) {
                    radarBlocked.set(index);
                }
            }
        }
    }

    private int cellX(double x) {
        return (int) Math.floor((x - minX) / resolution);
    }

    private int cellZ(double z) {
        return (int) Math.floor((z - minZ) / resolution);
    }

    private int index(int x, int z) {
        return z * width + x;
    }

    private static Bounds boundsFor(WorldMap worldMap, double resolution) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Landmass landmass : worldMap.landmasses()) {
            double distance = LandGeometry.radarBlockDistance(landmass);
            minX = Math.min(minX, landmass.x() - landmass.rx() * distance);
            maxX = Math.max(maxX, landmass.x() + landmass.rx() * distance);
            minZ = Math.min(minZ, landmass.z() - landmass.rz() * distance);
            maxZ = Math.max(maxZ, landmass.z() + landmass.rz() * distance);
        }
        if (!Double.isFinite(minX)) {
            minX = -resolution;
            maxX = resolution;
            minZ = -resolution;
            maxZ = resolution;
        }
        return new Bounds(
                Math.floor((minX - GRID_PADDING) / resolution) * resolution,
                Math.ceil((maxX + GRID_PADDING) / resolution) * resolution,
                Math.floor((minZ - GRID_PADDING) / resolution) * resolution,
                Math.ceil((maxZ + GRID_PADDING) / resolution) * resolution
        );
    }

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {
    }
}
