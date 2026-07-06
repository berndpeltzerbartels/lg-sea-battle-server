package one.xis.seabattle;

final class LandGeometry {

    private static final double LINE_SAMPLE_DISTANCE = 8.0;
    private static final double BLOCK_GRID_RESOLUTION = 4.0;
    private static final double COASTLINE_NAVIGATION_BLOCK_DISTANCE = 1.06;
    private static final double COASTLINE_RADAR_BLOCK_DISTANCE = 0.86;
    private static final double ISLAND_NAVIGATION_BLOCK_DISTANCE = 1.02;
    private static final double ISLAND_RADAR_BLOCK_DISTANCE = 0.92;
    private static final double STEEP_ROCK_BLOCK_DISTANCE = 1.0;
    private static volatile WorldMap cachedGridWorldMap;
    private static volatile LandBlockGrid cachedGrid;

    private LandGeometry() {
    }

    static boolean isBlocked(Vector2 position, WorldMap worldMap) {
        return isBlockedExact(position, worldMap, false);
    }

    static boolean isBlockedByLandmass(Vector2 position, Landmass landmass) {
        double distance = shapeDistance(position, landmass);
        return distance < navigationBlockDistance(landmass) && !isInLandWater(position, landmass);
    }

    static boolean isRadarBlockedByLandmass(Vector2 position, Landmass landmass) {
        double distance = shapeDistance(position, landmass);
        return distance < radarBlockDistance(landmass) && !isInLandWater(position, landmass);
    }

    static boolean lineIntersectsBlockedLand(Vector2 from, Vector2 to, WorldMap worldMap) {
        return lineIntersectsLand(from, to, worldMap, false);
    }

    static boolean lineIntersectsRadarBlockingLand(Vector2 from, Vector2 to, WorldMap worldMap) {
        return lineIntersectsLand(from, to, worldMap, true);
    }

    static void prepareRadarBlockingGrid(WorldMap worldMap) {
        gridFor(worldMap);
    }

    private static boolean lineIntersectsLand(Vector2 from, Vector2 to, WorldMap worldMap, boolean radarOnly) {
        double length = from.distanceTo(to);
        if (length <= 0.001) {
            return false;
        }
        int samples = Math.max(1, (int) Math.ceil(length / LINE_SAMPLE_DISTANCE));
        for (int i = 1; i < samples; i += 1) {
            double t = i / (double) samples;
            Vector2 sample = new Vector2(
                    from.x() + (to.x() - from.x()) * t,
                    from.z() + (to.z() - from.z()) * t
            );
            boolean blocked = radarOnly
                    ? gridFor(worldMap).isBlocked(sample)
                    : isBlockedExact(sample, worldMap, false);
            if (blocked) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedExact(Vector2 position, WorldMap worldMap, boolean radarOnly) {
        return worldMap.landmasses().stream().anyMatch(landmass -> {
            double distance = shapeDistance(position, landmass);
            double blockDistance = radarOnly ? radarBlockDistance(landmass) : navigationBlockDistance(landmass);
            return distance < blockDistance && !isInLandWater(position, landmass);
        });
    }

    private static LandBlockGrid gridFor(WorldMap worldMap) {
        LandBlockGrid grid = cachedGrid;
        if (grid != null && cachedGridWorldMap == worldMap) {
            return grid;
        }
        synchronized (LandGeometry.class) {
            grid = cachedGrid;
            if (grid != null && cachedGridWorldMap == worldMap) {
                return grid;
            }
            grid = LandBlockGrid.from(worldMap, BLOCK_GRID_RESOLUTION);
            cachedGridWorldMap = worldMap;
            cachedGrid = grid;
            return grid;
        }
    }

    static double shapeDistance(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        double nx = localX / landmass.rx();
        double nz = localZ / landmass.rz();
        double distance = Math.sqrt(nx * nx + nz * nz);
        if (!"coastline".equals(landmass.kind())) {
            return distance;
        }
        double angle = Math.atan2(nz, nx);
        return distance / coastRadiusFactor(angle, landmass);
    }

    static double navigationBlockDistance(Landmass landmass) {
        if ("coastline".equals(landmass.kind())) {
            return COASTLINE_NAVIGATION_BLOCK_DISTANCE;
        }
        return isSteepRock(landmass) ? STEEP_ROCK_BLOCK_DISTANCE : ISLAND_NAVIGATION_BLOCK_DISTANCE;
    }

    static double radarBlockDistance(Landmass landmass) {
        if ("coastline".equals(landmass.kind())) {
            return COASTLINE_RADAR_BLOCK_DISTANCE;
        }
        return isSteepRock(landmass) ? STEEP_ROCK_BLOCK_DISTANCE : ISLAND_RADAR_BLOCK_DISTANCE;
    }

    static boolean isInLandWater(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        return isInWaterway(localX, localZ, landmass)
                || isInLake(localX, localZ, landmass);
    }

    private static boolean isInWaterway(double localX, double localZ, Landmass landmass) {
        return landmass.waterways().stream().anyMatch(waterway ->
                distanceToSegment(localX, localZ, waterway.from().x(), waterway.from().z(),
                        waterway.to().x(), waterway.to().z()) <= waterway.width() * 0.58
        );
    }

    private static boolean isInLake(double localX, double localZ, Landmass landmass) {
        return landmass.lakes().stream().anyMatch(lake -> {
            double nx = (localX - lake.x()) / lake.rx();
            double nz = (localZ - lake.z()) / lake.rz();
            return nx * nx + nz * nz <= 1;
        });
    }

    private static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared == 0
                ? 0
                : MathSupport.clamp(((px - ax) * dx + (pz - az) * dz) / lengthSquared, 0, 1);
        double nearestX = ax + dx * t;
        double nearestZ = az + dz * t;
        double ox = px - nearestX;
        double oz = pz - nearestZ;
        return Math.sqrt(ox * ox + oz * oz);
    }

    private static double coastRadiusFactor(double angle, Landmass landmass) {
        double roughness = landmass.coastRoughness() == null ? 0.16 : landmass.coastRoughness();
        double seed = stableNameSeed(landmass.name()) * 0.013;
        double broad = Math.sin(angle * 2 + seed) * 0.62;
        double bays = Math.sin(angle * 4 - seed * 0.7) * 0.42;
        double small = Math.sin(angle * 7 + seed * 1.4) * 0.2;
        double fjordBite = 0;
        for (Fjord fjord : landmass.fjords()) {
            double width = Math.max(0.08, fjord.width());
            double angleDistance = Math.abs(MathSupport.normalizeAngle(angle - fjord.angle()));
            double mouth = 1 - MathSupport.smoothstep(width * 0.45, width * 1.9, angleDistance);
            fjordBite = Math.max(fjordBite, mouth * (0.18 + width * 0.9));
        }
        return MathSupport.clamp(1 + (broad + bays + small) * roughness - fjordBite, 0.56, 1.42);
    }

    private static int stableNameSeed(String name) {
        int seed = 0;
        for (int i = 0; i < name.length(); i += 1) {
            seed = (seed * 31 + name.charAt(i)) % 9973;
        }
        return seed;
    }

    private static boolean isSteepRock(Landmass landmass) {
        String name = landmass.name();
        return "island".equals(landmass.kind())
                && (name.contains("rock")
                || name.contains("rocks")
                || name.contains("stack")
                || name.contains("needle")
                || name.contains("skerry")
                || name.contains("skerries"));
    }
}
