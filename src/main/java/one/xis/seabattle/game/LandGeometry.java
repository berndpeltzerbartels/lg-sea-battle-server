package one.xis.seabattle.game;

import java.util.*;

final class LandGeometry {

    private static final double LINE_SAMPLE_DISTANCE = 8.0;
    private static final double COASTLINE_NAVIGATION_BLOCK_DISTANCE = 1.06;
    private static final double ISLAND_NAVIGATION_BLOCK_DISTANCE = 1.02;
    private static final double STEEP_ROCK_BLOCK_DISTANCE = 1.0;
    private static final int AUTHORED_COASTLINE_SMOOTHING_ITERATIONS = 2;
    private static final int AUTHORED_PLATEAU_SMOOTHING_ITERATIONS = 1;
    private static final double AUTHORED_HEIGHT_FIELD_CELL_SIZE = 16.0;
    private static final int AUTHORED_HEIGHT_FIELD_MAX_CELLS = 2_000_000;
    private static final double AUTHORED_HEIGHT_FIELD_EXACT_CHECK_MARGIN = 80.0;

    private LandGeometry() {
    }

    static boolean isBlocked(Vector2 position, WorldMap worldMap) {
        return isBlockedAtOrAbove(position, worldMap, 0);
    }

    static CollisionModel collisionModel(WorldMap worldMap) {
        return new CollisionModel(worldMap);
    }

    static boolean isBlockedAtOrAbove(Vector2 position, WorldMap worldMap, double minimumTerrainHeight) {
        return worldMap.landmasses().stream()
                .anyMatch(landmass -> isBlockedAtOrAbove(position, landmass, minimumTerrainHeight));
    }

    static boolean isBlockedAtOrAbove(Vector2 position, CollisionModel collisionModel, double minimumTerrainHeight) {
        return collisionModel.landmasses().stream()
                .anyMatch(landmass -> isBlockedAtOrAbove(position, landmass, minimumTerrainHeight));
    }

    static boolean isBlockedByLandmass(Vector2 position, Landmass landmass) {
        return isBlockedAtOrAbove(position, landmass, 0);
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
            if (isBlocked(sample, worldMap)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedAtOrAbove(Vector2 position, Landmass landmass, double minimumTerrainHeight) {
        if (hasAuthoredGeometry(landmass)) {
            return pointInAuthoredCoastline(position, landmass)
                    && authoredTerrainHeightAt(position, landmass) >= minimumTerrainHeight
                    && !isInLandWater(position, landmass);
        }
        if (minimumTerrainHeight <= 0) {
            return shapeDistance(position, landmass) < navigationBlockDistance(landmass)
                    && !isInLandWater(position, landmass);
        }
        return terrainHeightAt(position, landmass) >= minimumTerrainHeight
                && !isInLandWater(position, landmass);
    }

    private static boolean isBlockedAtOrAbove(Vector2 position, CollisionLandmass collisionLandmass,
                                              double minimumTerrainHeight) {
        Landmass landmass = collisionLandmass.landmass();
        if (hasAuthoredGeometry(landmass)) {
            if (!pointInPolygon(position, collisionLandmass.authoredCoastline())) {
                return false;
            }
            double sampledHeight = authoredTerrainHeightAt(position, collisionLandmass);
            if (sampledHeight >= minimumTerrainHeight + AUTHORED_HEIGHT_FIELD_EXACT_CHECK_MARGIN) {
                return !isInLandWater(position, landmass);
            }
            if (sampledHeight < minimumTerrainHeight - AUTHORED_HEIGHT_FIELD_EXACT_CHECK_MARGIN) {
                return false;
            }
            return authoredTerrainHeightAt(position, landmass, collisionLandmass.authoredCoastline()) >= minimumTerrainHeight
                    && !isInLandWater(position, landmass);
        }
        if (minimumTerrainHeight <= 0) {
            return shapeDistance(position, landmass) < navigationBlockDistance(landmass)
                    && !isInLandWater(position, landmass);
        }
        return terrainHeightAt(position, landmass) >= minimumTerrainHeight
                && !isInLandWater(position, landmass);
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
        if (hasAuthoredGeometry(landmass)) {
            return authoredTerrainHeightAt(position, landmass);
        }
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
        if (hasAuthoredGeometry(landmass)) {
            return landmass.heightPoints().stream()
                    .mapToDouble(HeightPoint::h)
                    .max()
                    .orElse(0);
        }
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

    private static boolean hasAuthoredGeometry(Landmass landmass) {
        return landmass.polygon().size() >= 3;
    }

    private static double authoredTerrainHeightAt(Vector2 position, Landmass landmass) {
        return authoredTerrainHeightAt(position, landmass, authoredCoastline(landmass));
    }

    private static double authoredTerrainHeightAt(Vector2 position, CollisionLandmass collisionLandmass) {
        HeightField heightField = collisionLandmass.heightField();
        if (heightField != null) {
            return heightField.heightAt(position);
        }
        return authoredTerrainHeightAt(position, collisionLandmass.landmass(), collisionLandmass.authoredCoastline());
    }

    private static double authoredTerrainHeightAt(Vector2 position, Landmass landmass, List<Point2> coastline) {
        if (!pointInPolygon(position, coastline)) {
            return seaFloorHeight(landmass);
        }
        List<HeightPoint> heightPoints = landmass.heightPoints();
        if (heightPoints.isEmpty()) {
            return terrainBaseHeight(landmass);
        }
        Plateau plateau = plateauAt(position, landmass);
        if (plateau != null) {
            return stackedPlateauHeightAt(position, landmass, plateau);
        }
        double height = terrainBaseHeight(landmass);
        for (HeightPoint point : heightPoints) {
            if (point.plateauGroupId() != null) {
                continue;
            }
            HeightBase base = heightPointBase(landmass, point, landmass.polygon());
            Double contribution = heightFromBasePolygon(position, point, base);
            if (contribution != null) {
                height = Math.max(height, contribution);
            }
        }
        return height;
    }

    private static double terrainBaseHeight(Landmass landmass) {
        return Math.max(seaFloorHeight(landmass), Math.min(8000, landmass.baseHeight()));
    }

    private static double stackedPlateauHeightAt(Vector2 position, Landmass landmass, Plateau plateau) {
        double height = plateau.height();
        for (HeightPoint point : landmass.heightPoints()) {
            if (!plateau.id().equals(point.basePlateauGroupId())) {
                continue;
            }
            HeightBase base = heightPointBase(landmass, point, plateau.polygon());
            Double contribution = heightFromBasePolygon(position, point, base);
            if (contribution != null) {
                height = Math.max(height, contribution);
            }
        }
        return height;
    }

    private static HeightBase heightPointBase(Landmass landmass, HeightPoint point, List<Point2> defaultBoundary) {
        List<Integer> baseIndexes = point.basePointIndexes().stream()
                .filter(index -> index >= 0 && index < landmass.polygon().size())
                .toList();
        if (baseIndexes.size() >= 3) {
            return new HeightBase(
                    baseIndexes.stream().map(landmass.polygon()::get).toList(),
                    terrainBaseHeight(landmass)
            );
        }
        if (point.basePlateauGroupId() != null) {
            for (Plateau plateau : plateausForLandmass(landmass)) {
                if (point.basePlateauGroupId().equals(plateau.id())) {
                    return new HeightBase(plateau.polygon(), plateau.height());
                }
            }
        }
        return new HeightBase(defaultBoundary, terrainBaseHeight(landmass));
    }

    private static Double heightFromBasePolygon(Vector2 position, HeightPoint point, HeightBase base) {
        if (base.polygon().size() < 3 || !pointInPolygon(position, base.polygon())) {
            return null;
        }
        for (int index = 0; index < base.polygon().size(); index += 1) {
            Point2 a = base.polygon().get(index);
            Point2 b = base.polygon().get((index + 1) % base.polygon().size());
            Double peakWeight = barycentricWeightForPoint(position, a, b, point);
            if (peakWeight != null) {
                double shapedWeight = heightProfileWeight(peakWeight, point.falloff());
                return base.floor() + (point.h() - base.floor()) * shapedWeight;
            }
        }
        return base.floor();
    }

    private static double heightProfileWeight(double linearWeight, String falloff) {
        double weight = MathSupport.clamp(linearWeight, 0, 1);
        if ("plateau".equals(falloff)) {
            return MathSupport.smoothstep(0, 0.58, weight);
        }
        if ("spike".equals(falloff)) {
            return weight;
        }
        return weight * weight * (3 - 2 * weight);
    }

    private static Double barycentricWeightForPoint(Vector2 position, Point2 a, Point2 b, HeightPoint peak) {
        double denominator = (b.z() - peak.z()) * (a.x() - peak.x())
                + (peak.x() - b.x()) * (a.z() - peak.z());
        if (Math.abs(denominator) < 0.000001) {
            return null;
        }
        double w1 = ((b.z() - peak.z()) * (position.x() - peak.x())
                + (peak.x() - b.x()) * (position.z() - peak.z())) / denominator;
        double w2 = ((peak.z() - a.z()) * (position.x() - peak.x())
                + (a.x() - peak.x()) * (position.z() - peak.z())) / denominator;
        double w3 = 1 - w1 - w2;
        double tolerance = -0.00001;
        if (w1 < tolerance || w2 < tolerance || w3 < tolerance) {
            return null;
        }
        return MathSupport.clamp(w3, 0, 1);
    }

    private static Plateau plateauAt(Vector2 position, Landmass landmass) {
        for (Plateau plateau : plateausForLandmass(landmass)) {
            if (pointInPolygon(position, plateau.polygon())) {
                return plateau;
            }
        }
        return null;
    }

    private static List<Plateau> plateausForLandmass(Landmass landmass) {
        Map<String, List<HeightPoint>> groups = new LinkedHashMap<>();
        for (HeightPoint point : landmass.heightPoints()) {
            if (point.plateauGroupId() == null) {
                continue;
            }
            groups.computeIfAbsent(point.plateauGroupId(), ignored -> new ArrayList<>()).add(point);
        }
        List<Plateau> plateaus = new ArrayList<>();
        for (Map.Entry<String, List<HeightPoint>> entry : groups.entrySet()) {
            if (entry.getValue().size() < 3) {
                continue;
            }
            List<Point2> polygon = orderedPlateauPoints(entry.getValue()).stream()
                    .map(point -> new Point2(point.x(), point.z()))
                    .toList();
            polygon = smoothClosedPolygon(polygon, AUTHORED_PLATEAU_SMOOTHING_ITERATIONS);
            double height = entry.getValue().stream().mapToDouble(HeightPoint::h).average().orElse(0);
            plateaus.add(new Plateau(entry.getKey(), polygon, height));
        }
        return plateaus;
    }

    private static List<HeightPoint> sortPointsAroundCenter(List<HeightPoint> points) {
        double centerX = points.stream().mapToDouble(HeightPoint::x).average().orElse(0);
        double centerZ = points.stream().mapToDouble(HeightPoint::z).average().orElse(0);
        return points.stream()
                .sorted(Comparator.comparingDouble(point -> Math.atan2(point.z() - centerZ, point.x() - centerX)))
                .toList();
    }

    private static List<HeightPoint> orderedPlateauPoints(List<HeightPoint> points) {
        List<HeightPoint> ordered = points.stream()
                .sorted(Comparator.comparing(point -> point.plateauOrder() == null ? Integer.MAX_VALUE : point.plateauOrder()))
                .toList();
        for (int index = 0; index < ordered.size(); index += 1) {
            if (ordered.get(index).plateauOrder() == null || ordered.get(index).plateauOrder() != index) {
                return sortPointsAroundCenter(points);
            }
        }
        return isSimplePolygon(ordered) ? ordered : sortPointsAroundCenter(points);
    }

    private static boolean isSimplePolygon(List<HeightPoint> points) {
        if (points.size() < 4) {
            return points.size() >= 3;
        }
        for (int index = 0; index < points.size(); index += 1) {
            HeightPoint a = points.get(index);
            HeightPoint b = points.get((index + 1) % points.size());
            for (int other = index + 1; other < points.size(); other += 1) {
                if (other == index || other == (index + 1) % points.size() || (other + 1) % points.size() == index) {
                    continue;
                }
                HeightPoint c = points.get(other);
                HeightPoint d = points.get((other + 1) % points.size());
                if (segmentsIntersect(a, b, c, d)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentsIntersect(HeightPoint a, HeightPoint b, HeightPoint c, HeightPoint d) {
        double o1 = triangleOrientation(a, b, c);
        double o2 = triangleOrientation(a, b, d);
        double o3 = triangleOrientation(c, d, a);
        double o4 = triangleOrientation(c, d, b);
        return o1 * o2 < 0 && o3 * o4 < 0;
    }

    private static double triangleOrientation(HeightPoint a, HeightPoint b, HeightPoint c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }

    private static boolean pointInAuthoredCoastline(Vector2 position, Landmass landmass) {
        return pointInPolygon(position, authoredCoastline(landmass));
    }

    private static List<Point2> authoredCoastline(Landmass landmass) {
        return smoothClosedPolygon(landmass.polygon(), AUTHORED_COASTLINE_SMOOTHING_ITERATIONS);
    }

    private static List<Point2> smoothClosedPolygon(List<Point2> points, int iterations) {
        if (points.size() < 3 || iterations <= 0) {
            return points;
        }
        List<Point2> smoothed = List.copyOf(points);
        for (int iteration = 0; iteration < iterations; iteration += 1) {
            java.util.ArrayList<Point2> next = new java.util.ArrayList<>(smoothed.size() * 2);
            for (int index = 0; index < smoothed.size(); index += 1) {
                Point2 current = smoothed.get(index);
                Point2 following = smoothed.get((index + 1) % smoothed.size());
                next.add(new Point2(
                        current.x() * 0.75 + following.x() * 0.25,
                        current.z() * 0.75 + following.z() * 0.25
                ));
                next.add(new Point2(
                        current.x() * 0.25 + following.x() * 0.75,
                        current.z() * 0.25 + following.z() * 0.75
                ));
            }
            smoothed = List.copyOf(next);
        }
        return smoothed;
    }

    private static double seaFloorHeight(Landmass landmass) {
        return Math.min(-1, Math.max(-2000, Math.round(landmass.seaFloorHeight())));
    }

    private static double falloffHeightMultiplier(double normalizedDistance, String falloff) {
        double t = MathSupport.clamp(1 - normalizedDistance, 0, 1);
        if ("hill".equals(falloff)) {
            return t * t * (3 - 2 * t);
        }
        if ("plateau".equals(falloff)) {
            return 1 - MathSupport.smoothstep(0.38, 1, normalizedDistance);
        }
        return t * t;
    }

    private static double falloffWeightMultiplier(double normalizedDistance, String falloff) {
        if (normalizedDistance >= 1) {
            return 0.08;
        }
        return 0.18 + falloffHeightMultiplier(normalizedDistance, falloff) * 1.82;
    }

    private static boolean pointInPolygon(Vector2 point, List<Point2> polygon) {
        boolean inside = false;
        for (int index = 0, previous = polygon.size() - 1; index < polygon.size(); previous = index, index += 1) {
            Point2 current = polygon.get(index);
            Point2 previousPoint = polygon.get(previous);
            boolean crosses = current.z() > point.z() != previousPoint.z() > point.z()
                    && point.x() < ((previousPoint.x() - current.x()) * (point.z() - current.z()))
                    / (previousPoint.z() - current.z()) + current.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
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

    record CollisionModel(WorldMap worldMap, List<CollisionLandmass> landmasses) {
        CollisionModel(WorldMap worldMap) {
            this(worldMap, worldMap.landmasses().stream()
                    .map(CollisionLandmass::from)
                    .toList());
        }
    }

    private record CollisionLandmass(Landmass landmass, List<Point2> authoredCoastline, HeightField heightField) {
        static CollisionLandmass from(Landmass landmass) {
            List<Point2> coastline = hasAuthoredGeometry(landmass)
                    ? LandGeometry.authoredCoastline(landmass)
                    : List.of();
            return new CollisionLandmass(landmass, coastline, HeightField.from(landmass, coastline));
        }
    }

    private record HeightField(double minX, double minZ, int columns, int rows, double[] heights) {
        static HeightField from(Landmass landmass, List<Point2> coastline) {
            if (coastline.isEmpty()) {
                return null;
            }
            double minX = coastline.stream().mapToDouble(Point2::x).min().orElse(0);
            double maxX = coastline.stream().mapToDouble(Point2::x).max().orElse(0);
            double minZ = coastline.stream().mapToDouble(Point2::z).min().orElse(0);
            double maxZ = coastline.stream().mapToDouble(Point2::z).max().orElse(0);
            int columns = Math.max(1, (int) Math.ceil((maxX - minX) / AUTHORED_HEIGHT_FIELD_CELL_SIZE) + 1);
            int rows = Math.max(1, (int) Math.ceil((maxZ - minZ) / AUTHORED_HEIGHT_FIELD_CELL_SIZE) + 1);
            if ((long) columns * rows > AUTHORED_HEIGHT_FIELD_MAX_CELLS) {
                return null;
            }

            double[] heights = new double[columns * rows];
            for (int row = 0; row < rows; row += 1) {
                double z = minZ + row * AUTHORED_HEIGHT_FIELD_CELL_SIZE;
                for (int column = 0; column < columns; column += 1) {
                    double x = minX + column * AUTHORED_HEIGHT_FIELD_CELL_SIZE;
                    Vector2 position = new Vector2(x, z);
                    heights[row * columns + column] = pointInPolygon(position, coastline)
                            ? authoredTerrainHeightAt(position, landmass, coastline)
                            : Double.NEGATIVE_INFINITY;
                }
            }
            return new HeightField(minX, minZ, columns, rows, heights);
        }

        double heightAt(Vector2 position) {
            int column = (int) Math.round((position.x() - minX) / AUTHORED_HEIGHT_FIELD_CELL_SIZE);
            int row = (int) Math.round((position.z() - minZ) / AUTHORED_HEIGHT_FIELD_CELL_SIZE);
            column = Math.max(0, Math.min(columns - 1, column));
            row = Math.max(0, Math.min(rows - 1, row));
            return heights[row * columns + column];
        }
    }

    private record Plateau(String id, List<Point2> polygon, double height) {
    }

    private record HeightBase(List<Point2> polygon, double floor) {
    }
}
