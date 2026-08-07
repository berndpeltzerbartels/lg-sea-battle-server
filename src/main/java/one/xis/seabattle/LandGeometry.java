package one.xis.seabattle;

import java.util.List;

final class LandGeometry {

    private static final double LINE_SAMPLE_DISTANCE = 8.0;
    private static final double COASTLINE_NAVIGATION_BLOCK_DISTANCE = 1.06;
    private static final double ISLAND_NAVIGATION_BLOCK_DISTANCE = 1.02;
    private static final double STEEP_ROCK_BLOCK_DISTANCE = 1.0;

    private LandGeometry() {
    }

    static boolean isBlocked(Vector2 position, WorldMap worldMap) {
        return isBlockedExact(position, worldMap);
    }

    static boolean isBlockedByLandmass(Vector2 position, Landmass landmass) {
        double distance = shapeDistance(position, landmass);
        return distance < navigationBlockDistance(landmass) && !isInLandWater(position, landmass);
    }

    static boolean lineIntersectsBlockedLand(Vector2 from, Vector2 to, WorldMap worldMap) {
        return lineIntersectsLand(from, to, worldMap);
    }

    static double terrainHeightAt(Vector2 position, WorldMap worldMap) {
        double height = 0;
        for (Landmass landmass : worldMap.landmasses()) {
            if (isInLandWater(position, landmass)) {
                continue;
            }
            height = Math.max(height, terrainHeightAt(position, landmass));
        }
        return height;
    }

    static double maxTerrainHeight(WorldMap worldMap) {
        double height = 0;
        for (Landmass landmass : worldMap.landmasses()) {
            height = Math.max(height, maxTerrainHeight(landmass));
        }
        return height;
    }

    static WorldMap obstacleMapForMinimumTerrainHeight(WorldMap worldMap, double minimumTerrainHeight) {
        List<Landmass> obstacles = worldMap.landmasses().stream()
                .filter(landmass -> maxTerrainHeight(landmass) >= minimumTerrainHeight)
                .toList();
        return obstacles.size() == worldMap.landmasses().size()
                ? worldMap
                : new WorldMap(worldMap.version(), obstacles);
    }

    private static boolean lineIntersectsLand(Vector2 from, Vector2 to, WorldMap worldMap) {
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
            if (isBlockedExact(sample, worldMap)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedExact(Vector2 position, WorldMap worldMap) {
        return worldMap.landmasses().stream().anyMatch(landmass -> {
            double distance = shapeDistance(position, landmass);
            return distance < navigationBlockDistance(landmass) && !isInLandWater(position, landmass);
        });
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

    static boolean isInLandWater(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        return isInWaterway(localX, localZ, landmass)
                || isInLake(localX, localZ, landmass);
    }

    private static double terrainHeightAt(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        double distance = shapeDistance(position, landmass);
        if (distance >= 1.02) {
            return 0;
        }
        if (isSteepRock(landmass)) {
            double radius = landmass.radius() == null ? Math.min(landmass.rx(), landmass.rz()) : landmass.radius();
            return Math.max(0.6, radius * 0.42 * landmass.heightScale() * (1 - MathSupport.smoothstep(0.62, 1.02, distance)));
        }
        if ("coastline".equals(landmass.kind())) {
            return coastlineTerrainHeight(localX, localZ, distance, landmass);
        }
        return islandTerrainHeight(localX, localZ, distance, landmass);
    }

    private static double maxTerrainHeight(Landmass landmass) {
        if (isSteepRock(landmass)) {
            double radius = landmass.radius() == null ? Math.min(landmass.rx(), landmass.rz()) : landmass.radius();
            return Math.max(0.6, radius * 0.42 * landmass.heightScale());
        }
        if ("coastline".equals(landmass.kind())) {
            double peakBoost = landmass.peakBoost() == null ? 0 : landmass.peakBoost();
            return 0.48 + 5.5 + 24 * landmass.heightScale() + peakBoost + 3.2;
        }
        return 0.34 + Math.max(1.1, Math.min(4.2, Math.min(landmass.rx(), landmass.rz()) * 0.15 * landmass.heightScale()));
    }

    private static double coastlineTerrainHeight(double localX, double localZ, double ring, Landmass landmass) {
        if (ring >= 0.98) {
            return 0;
        }
        double nx = localX / landmass.rx();
        double nz = localZ / landmass.rz();
        double inland = MathSupport.clamp(1 - ring, 0, 1);
        double ridgeA = Math.sin(localX * 0.065 + localZ * 0.035) * 0.5 + 0.5;
        double ridgeB = Math.sin(localX * -0.028 + localZ * 0.082 + 2.4) * 0.5 + 0.5;
        double roughness = terrainNoise(localX, localZ);
        double cliffLift = MathSupport.smoothstep(0.68, 0.9, ring) * MathSupport.smoothstep(1.04, 0.86, ring) * 5.5;
        double mountainLift = Math.pow(inland, 0.65) * (9 + ridgeA * 10 + ridgeB * 5) * landmass.heightScale();
        double peakLift = peakLift(nx, nz, ring, landmass);
        double shoreBlend = 1 - MathSupport.smoothstep(0.9, 0.98, ring);
        return 0.28 + shoreBlend * (0.2 + cliffLift + mountainLift + peakLift + roughness * 3.2);
    }

    private static double islandTerrainHeight(double localX, double localZ, double ring, Landmass landmass) {
        if (ring >= 1.0) {
            return 0;
        }
        double radius = landmass.radius() == null ? Math.min(landmass.rx(), landmass.rz()) : landmass.radius();
        double seed = stableNameSeed(landmass.name());
        double hillRx = landmass.rx() * (0.72 + ((int) seed % 5) * 0.018);
        double hillRz = landmass.rz() * (0.62 + ((int) seed % 7) * 0.014);
        double height = Math.max(1.1, Math.min(4.2, Math.min(landmass.rx(), landmass.rz()) * 0.15 * landmass.heightScale()));
        double peakAngle = seed * 0.017;
        double peakX = Math.cos(peakAngle) * hillRx * 0.16;
        double peakZ = Math.sin(peakAngle) * hillRz * 0.16;
        double nx = (localX - peakX) / Math.max(1, hillRx);
        double nz = (localZ - peakZ) / Math.max(1, hillRz);
        double hillDistance = Math.sqrt(nx * nx + nz * nz);
        double crown = Math.pow(MathSupport.clamp(1 - hillDistance, 0, 1), 0.72);
        return 0.34 + height * crown * (1 - MathSupport.smoothstep(0.72, 1.0, ring) * 0.9);
    }

    private static double peakLift(double nx, double nz, double ring, Landmass landmass) {
        double peakBoost = landmass.peakBoost() == null ? 0 : landmass.peakBoost();
        if (landmass.caldera() == null) {
            return peakBoost * Math.pow(MathSupport.clamp(1 - Math.sqrt((nx * 1.35) * (nx * 1.35) + (nz * 1.15) * (nz * 1.15)), 0, 1), 2.4);
        }

        Caldera caldera = landmass.caldera();
        double radius = caldera.radius();
        double rim = caldera.rim();
        double depth = caldera.depth();
        double craterDistance = Math.sqrt((nx * 1.18) * (nx * 1.18) + (nz * 1.05) * (nz * 1.05));
        double outerCone = peakBoost * Math.pow(MathSupport.clamp(1 - ring * 0.72, 0, 1), 2.1);
        double rimLift = peakBoost * 0.48 * Math.exp(-((craterDistance - radius) * (craterDistance - radius)) / (rim * rim));
        double bowlDrop = depth * (1 - MathSupport.smoothstep(radius * 0.45, radius, craterDistance));
        return Math.max(0, outerCone + rimLift - bowlDrop);
    }

    private static double terrainNoise(double x, double z) {
        return Math.sin(x * 0.17 + z * 0.08) * 0.45
                + Math.sin(x * 0.07 - z * 0.19 + 1.7) * 0.35
                + Math.sin(x * -0.13 + z * 0.12 + 4.1) * 0.2;
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
