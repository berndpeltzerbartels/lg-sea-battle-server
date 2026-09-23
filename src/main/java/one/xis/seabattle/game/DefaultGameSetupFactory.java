package one.xis.seabattle.game;

import one.xis.context.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

@Service
final class DefaultGameSetupFactory {

    private static final Logger LOGGER = Logger.getLogger(DefaultGameSetupFactory.class.getName());
    private static final double CUSTOM_RESPAWN_BOUNDS_PADDING_RATIO = 0.05;
    private static final double CUSTOM_RESPAWN_GRID_STEP = 180;
    private static final double CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE = 1000;
    private static final double CUSTOM_RESPAWN_MIN_BOUNDS_SIZE = CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE * 2;
    private static final double CUSTOM_RESPAWN_MIN_BOUNDS_PADDING = CUSTOM_RESPAWN_GRID_STEP * 2;
    private static final double CUSTOM_RESPAWN_MIN_CANDIDATE_DISTANCE = 150;
    private static final double CUSTOM_RESPAWN_MIN_LAND_DISTANCE = 80;
    private static final int CUSTOM_RESPAWN_MIN_CANDIDATES = 64;
    private static final int ENGINE_STOP = 2;
    private static final int ENGINE_SLOW = 3;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;
    private static final String TEAM_DARK = "dark";
    private static final String TEAM_LIGHT = "light";
    private static final String TEAM_GREEN = "green";
    private static final String TEAM_SAND = "sand";
    private static final String VEHICLE_TORPEDO_BOAT = "torpedo-boat";
    private static final String VEHICLE_SCOUT_PLANE = "scout-plane";
    private static final double SCOUT_PLANE_START_Y = 170;
    private static final List<String> BASE_TEAMS = List.of(TEAM_DARK, TEAM_LIGHT);
    private static final List<String> TEAM_ORDER = List.of(TEAM_DARK, TEAM_LIGHT, TEAM_GREEN, TEAM_SAND);
    private static final String BOMB_DROP_SCENARIO = """
            scenario: scenario-bomb-drop
            version: 9101
            cell: 80
            map:
            ...........
            ...........
            ....1..2...
            ...........
            ...........
            objects:
            1: ship, light, human [orientation: 90°, speed: 0knt]
            2: plane, dark, bot [orientation: 270°, speed: 30knt, height: 170m]
            """;

    private final WorldMapService worldMapService;

    DefaultGameSetupFactory(WorldMapService worldMapService) {
        this.worldMapService = worldMapService;
    }

    GameSetup setup(String setupId) {
        return setup(setupId, List.of());
    }

    GameSetup setup(String setupId, List<String> requestedTeamIds) {
        if (setupId == null || setupId.isBlank() || "default".equals(setupId)) {
            return defaultSetup(requestedTeamIds);
        }
        return switch (setupId) {
            case "islands" -> openIslandsSetup();
            case "single-island" -> singleIslandSetup();
            case "ram-side" -> ramSideSetup();
            case "side-view-sandbox" -> sideViewSandboxSetup();
            case "two-ship-duel" -> twoShipDuelSetup();
            case "two-ship-duel-air" -> twoShipDuelAirSetup();
            case "explosion-demo" -> explosionDemoSetup();
            case "escort-debug" -> escortDebugSetup();
            case "landmark-tour" -> landmarkTourSetup();
            case "fleet-clash" -> fleetClashSetup();
            case "dense-land-crowded" -> fleetClashSetup();
            case "dense-land-crowded-reverse" -> fleetClashReverseSetup();
            case "scenario-bomb-drop" -> ScenarioScriptParser.parse(BOMB_DROP_SCENARIO);
            case "scout-plane" -> scoutPlaneSetup(activeTeamIds(requestedTeamIds));
            case "dense-land" -> denseLandSetup(activeTeamIds(requestedTeamIds));
            default -> throw new IllegalArgumentException("Unknown game setup: " + setupId);
        };
    }

    GameSetup defaultSetup() {
        return defaultSetup(List.of());
    }

    GameSetup defaultSetup(List<String> requestedTeamIds) {
        return denseLandSetup(activeTeamIds(requestedTeamIds));
    }

    GameSetup customLandscapeSetup(String landscapeId, WorldMap worldMap, List<String> requestedTeamIds) {
        return customLandscapeSetup("default", landscapeId, worldMap, requestedTeamIds);
    }

    GameSetup customLandscapeSetup(String setupId, String landscapeId, WorldMap worldMap, List<String> requestedTeamIds) {
        return customLandscapeSetup(setupId, landscapeId, worldMap, generatedWaterRespawnCandidates(worldMap), requestedTeamIds);
    }

    GameSetup customLandscapeSetup(String setupId, String landscapeId, WorldMap worldMap, List<Vector2> preparedRespawnCandidates,
                                   List<String> requestedTeamIds) {
        if (worldMap == null) {
            return defaultSetup(requestedTeamIds);
        }
        GameSetup baseSetup = setup(setupId, requestedTeamIds);
        List<Vector2> respawnCandidates = preparedRespawnCandidates == null
                ? List.of()
                : List.copyOf(preparedRespawnCandidates);
        if (respawnCandidates.isEmpty()) {
            respawnCandidates = baseSetup.respawnCandidates();
        }
        return new GameSetup(
                "landscape-" + (landscapeId == null || landscapeId.isBlank() ? "custom" : landscapeId)
                        + "-" + (setupId == null || setupId.isBlank() ? "default" : setupId),
                worldMap,
                placeFleetsOnCandidates(baseSetup.fleets(), respawnCandidates, worldMap),
                respawnCandidates
        );
    }

    static List<Vector2> generatedWaterRespawnCandidates(WorldMap worldMap) {
        long startedAtNanos = System.nanoTime();
        Bounds bounds = worldMapBounds(worldMap);
        double width = Math.max(1, bounds.maxX() - bounds.minX());
        double height = Math.max(1, bounds.maxZ() - bounds.minZ());
        double basePadding = Math.max(CUSTOM_RESPAWN_MIN_BOUNDS_PADDING,
                Math.max(width, height) * CUSTOM_RESPAWN_BOUNDS_PADDING_RATIO);
        List<RespawnLandDistance> landDistances = respawnLandDistances(worldMap);
        List<Vector2> bestCandidates = List.of();
        int testedPoints = 0;
        for (int attempt = 1; attempt <= 5; attempt += 1) {
            CandidateScan scan = generatedWaterRespawnCandidates(bounds, basePadding * attempt, landDistances);
            testedPoints += scan.testedPoints();
            List<Vector2> candidates = scan.candidates();
            if (candidates.size() >= CUSTOM_RESPAWN_MIN_CANDIDATES) {
                return logGeneratedWaterRespawnCandidates(worldMap, stableSpread(candidates), testedPoints, startedAtNanos);
            }
            if (candidates.size() > bestCandidates.size()) {
                bestCandidates = candidates;
            }
        }
        List<Vector2> candidates = bestCandidates.isEmpty() ? List.of() : stableSpread(bestCandidates);
        return logGeneratedWaterRespawnCandidates(worldMap, candidates, testedPoints, startedAtNanos);
    }

    private static CandidateScan generatedWaterRespawnCandidates(Bounds bounds, double padding,
                                                                 List<RespawnLandDistance> landDistances) {
        double minX = bounds.minX() - padding;
        double maxX = bounds.maxX() + padding;
        double minZ = bounds.minZ() - padding;
        double maxZ = bounds.maxZ() + padding;
        List<Vector2> candidates = new ArrayList<>();
        int testedPoints = 0;
        for (double z = minZ; z <= maxZ; z += CUSTOM_RESPAWN_GRID_STEP) {
            for (double x = minX; x <= maxX; x += CUSTOM_RESPAWN_GRID_STEP) {
                testedPoints += 1;
                Vector2 candidate = new Vector2(Math.round(x), Math.round(z));
                if (!hasMinimumLandDistance(candidate, landDistances)) {
                    continue;
                }
                if (tooCloseToGeneratedCandidate(candidate, candidates)) {
                    continue;
                }
                candidates.add(candidate);
            }
        }
        return new CandidateScan(candidates, testedPoints);
    }

    private static List<Vector2> logGeneratedWaterRespawnCandidates(WorldMap worldMap, List<Vector2> candidates,
                                                                    int testedPoints, long startedAtNanos) {
        double elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000.0;
        LOGGER.info(() -> "Prepared " + candidates.size() + " respawn candidates for "
                + worldMap.landmasses().size() + " landmasses from " + testedPoints + " scanned points in "
                + MathSupport.round(elapsedMillis) + " ms");
        return candidates;
    }

    private static boolean hasMinimumLandDistance(Vector2 candidate, List<RespawnLandDistance> landDistances) {
        for (RespawnLandDistance landDistance : landDistances) {
            if (landDistance.distanceFromLand(candidate) < CUSTOM_RESPAWN_MIN_LAND_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    private static List<RespawnLandDistance> respawnLandDistances(WorldMap worldMap) {
        return worldMap.landmasses().stream()
                .map(DefaultGameSetupFactory::respawnLandDistance)
                .toList();
    }

    private static RespawnLandDistance respawnLandDistance(Landmass landmass) {
        if (landmass.polygon().size() >= 3) {
            return new PolygonRespawnLandDistance(landmass.polygon());
        }
        return new EllipseRespawnLandDistance(landmass);
    }

    private static boolean tooCloseToGeneratedCandidate(Vector2 candidate, List<Vector2> candidates) {
        return candidates.stream().anyMatch(existing -> existing.distanceTo(candidate) < CUSTOM_RESPAWN_MIN_CANDIDATE_DISTANCE);
    }

    private static List<Vector2> stableSpread(List<Vector2> candidates) {
        return candidates.stream()
                .sorted((left, right) -> Double.compare(stableRespawnOrder(left), stableRespawnOrder(right)))
                .toList();
    }

    private static double stableRespawnOrder(Vector2 position) {
        return Math.sin(position.x() * 12.9898 + position.z() * 78.233) * 43758.5453
                - Math.floor(Math.sin(position.x() * 12.9898 + position.z() * 78.233) * 43758.5453);
    }

    private static Bounds worldMapBounds(WorldMap worldMap) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Landmass landmass : worldMap.landmasses()) {
            if (!landmass.polygon().isEmpty()) {
                for (Point2 point : landmass.polygon()) {
                    minX = Math.min(minX, point.x());
                    maxX = Math.max(maxX, point.x());
                    minZ = Math.min(minZ, point.z());
                    maxZ = Math.max(maxZ, point.z());
                }
            } else {
                double rx = Math.max(landmass.rx(), landmass.navigationRx());
                double rz = Math.max(landmass.rz(), landmass.navigationRz());
                minX = Math.min(minX, landmass.x() - rx);
                maxX = Math.max(maxX, landmass.x() + rx);
                minZ = Math.min(minZ, landmass.z() - rz);
                maxZ = Math.max(maxZ, landmass.z() + rz);
            }
        }
        if (!Double.isFinite(minX)) {
            return new Bounds(
                    -CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE,
                    -CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE,
                    CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE,
                    CUSTOM_RESPAWN_OPEN_WATER_HALF_SIZE
            );
        }
        double centerX = (minX + maxX) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double halfWidth = Math.max(CUSTOM_RESPAWN_MIN_BOUNDS_SIZE * 0.5, (maxX - minX) * 0.5);
        double halfHeight = Math.max(CUSTOM_RESPAWN_MIN_BOUNDS_SIZE * 0.5, (maxZ - minZ) * 0.5);
        return new Bounds(
                centerX - halfWidth,
                centerZ - halfHeight,
                centerX + halfWidth,
                centerZ + halfHeight
        );
    }

    private static List<FleetSetup> placeFleetsOnCandidates(List<FleetSetup> fleets, List<Vector2> candidates, WorldMap worldMap) {
        if (candidates.isEmpty()) {
            return fleets;
        }
        Vector2 center = worldCenter(worldMap);
        List<Vector2> shuffledCandidates = new ArrayList<>(candidates);
        Collections.shuffle(shuffledCandidates);
        int totalShips = fleets.stream().mapToInt(fleet -> fleet.ships().size()).sum();
        int spacing = Math.max(1, shuffledCandidates.size() / Math.max(1, totalShips));
        int cursor = ThreadLocalRandom.current().nextInt(Math.max(1, shuffledCandidates.size()));
        List<FleetSetup> placedFleets = new ArrayList<>();
        for (FleetSetup fleet : fleets) {
            List<ShipSetup> ships = new ArrayList<>();
            for (ShipSetup ship : fleet.ships()) {
                Vector2 position = shuffledCandidates.get(Math.floorMod(cursor, shuffledCandidates.size()));
                cursor += spacing;
                double heading = MathSupport.normalizeAngle(angleTo(center, position) + Math.PI
                        + ThreadLocalRandom.current().nextDouble(-0.35, 0.35));
                ships.add(new ShipSetup(
                        ship.id(),
                        ship.teamId(),
                        position,
                        heading,
                        ship.controlledBy(),
                        ship.engineOrder(),
                        ship.rudderDegrees(),
                        ship.nextFireDelaySeconds(),
                        ship.vehicleType(),
                        ship.y()
                ));
            }
            placedFleets.add(new FleetSetup(fleet.teamId(), ships));
        }
        return placedFleets;
    }

    private static Vector2 worldCenter(WorldMap worldMap) {
        Bounds bounds = worldMapBounds(worldMap);
        return new Vector2((bounds.minX() + bounds.maxX()) * 0.5, (bounds.minZ() + bounds.maxZ()) * 0.5);
    }

    private static double angleTo(Vector2 target, Vector2 origin) {
        return Math.atan2(target.x() - origin.x(), target.z() - origin.z());
    }

    private static double polygonDistance(Vector2 position, List<Point2> polygon) {
        if (polygon.size() < 3) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < polygon.size(); index += 1) {
            Point2 a = polygon.get(index);
            Point2 b = polygon.get((index + 1) % polygon.size());
            distance = Math.min(distance, distanceToSegment(position.x(), position.z(), a.x(), a.z(), b.x(), b.z()));
        }
        return pointInPolygon(position, polygon) ? -distance : distance;
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

    private static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared == 0
                ? 0
                : MathSupport.clamp(((px - ax) * dx + (pz - az) * dz) / lengthSquared, 0, 1);
        double nearestX = ax + dx * t;
        double nearestZ = az + dz * t;
        return Math.hypot(px - nearestX, pz - nearestZ);
    }

    private GameSetup openIslandsSetup() {
        return new GameSetup(
                "islands",
                worldMapService.world(),
                List.of(
                        new FleetSetup(TEAM_DARK, createShips(TEAM_DARK, redFormation())),
                        new FleetSetup(TEAM_LIGHT, createShips(TEAM_LIGHT, blueFormation()))
                ),
                respawnCandidates()
        );
    }

    private GameSetup singleIslandSetup() {
        WorldMap worldMap = new WorldMap(1001, List.of(
                new Landmass(
                        "island",
                        "test_island",
                        0,
                        0,
                        90,
                        64,
                        52,
                        37,
                        80,
                        58,
                        42.0,
                        1.0,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                )
        ));
        return new GameSetup(
                "single-island",
                worldMap,
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", -180, -115, 0.72, ENGINE_STOP, 0, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 180, 115, -2.4, ENGINE_STOP, 0, 0)))
                ),
                List.of(new Vector2(-180, -115), new Vector2(180, 115))
        );
    }

    private GameSetup ramSideSetup() {
        return new GameSetup(
                "ram-side",
                new WorldMap(1002, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "scenario", ENGINE_HALF, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, "scenario", ENGINE_STOP, 0, 99)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        );
    }

    private GameSetup sideViewSandboxSetup() {
        return new GameSetup(
                "side-view-sandbox",
                new WorldMap(1003, List.of()),
                List.of(
                        new FleetSetup(TEAM_DARK, List.of(
                                ship("dark-S1", TEAM_DARK, 0, -24, 0, ENGINE_STOP, 0, 99)
                        )),
                        new FleetSetup(TEAM_LIGHT, List.of(
                                ship("light-S1", TEAM_LIGHT, 0, 26, Math.PI / 2, ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, -24), new Vector2(0, 26))
        );
    }

    private GameSetup twoShipDuelSetup() {
        return new GameSetup(
                "two-ship-duel",
                worldMapService.denseWorld(),
                List.of(
                        new FleetSetup(TEAM_LIGHT, List.of(
                                ship("light-S1", TEAM_LIGHT, -40, -480, Math.PI / 2, "scenario", ENGINE_STOP, 0, 99)
                        )),
                        new FleetSetup(TEAM_DARK, List.of(
                                ship("dark-S1", TEAM_DARK, 195, -505, -Math.PI / 2, "scenario", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(-40, -480), new Vector2(195, -505))
        );
    }

    private GameSetup twoShipDuelAirSetup() {
        return new GameSetup(
                "two-ship-duel-air",
                worldMapService.denseWorld(),
                List.of(
                        new FleetSetup(TEAM_LIGHT, List.of(
                                ship("light-S1", TEAM_LIGHT, -40, -480, Math.PI / 2, "scenario", ENGINE_STOP, 0, 99),
                                scoutPlane("light-F1", TEAM_LIGHT, -413, -620, Math.PI / 2, ENGINE_HALF, 0, 2)
                        )),
                        new FleetSetup(TEAM_DARK, List.of(
                                ship("dark-S1", TEAM_DARK, 195, -505, -Math.PI / 2, "scenario", ENGINE_STOP, 0, 99),
                                scoutPlane("dark-F1", TEAM_DARK, 543, -365, -Math.PI / 2, ENGINE_HALF, 0, 2)
                        ))
                ),
                List.of(new Vector2(-40, -480), new Vector2(195, -505))
        );
    }

    private GameSetup explosionDemoSetup() {
        WorldMap worldMap = worldMapService.world();
        return new GameSetup(
                "explosion-demo",
                worldMap,
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, -2100, 0.12, ENGINE_STOP, 0, 99),
                                ship("red-2", "red", 70, -540, Math.PI / 2, ENGINE_STOP, 0, 0),
                                ship("red-3", "red", 90, -500, Math.PI / 2, ENGINE_STOP, 0, 0.1),
                                ship("red-4", "red", 55, -460, Math.PI / 2, ENGINE_STOP, 0, 0.2),
                                ship("red-5", "red", 99, -403, Math.PI / 2, ENGINE_STOP, 0, 0.3),
                                ship("red-6", "red", 75, -380, Math.PI / 2, ENGINE_STOP, 0, 0.4),
                                ship("red-7", "red", 125, -340, Math.PI / 2, ENGINE_STOP, 0, 0.5),
                                ship("red-8", "red", -70, -520, Math.PI / 2, ENGINE_STOP, 0, 0.15),
                                ship("red-9", "red", -45, -480, Math.PI / 2, ENGINE_STOP, 0, 0.25),
                                ship("red-10", "red", -85, -440, Math.PI / 2, ENGINE_STOP, 0, 0.35),
                                ship("red-11", "red", -35, -400, Math.PI / 2, ENGINE_STOP, 0, 0.45),
                                ship("red-12", "red", -75, -360, Math.PI / 2, ENGINE_STOP, 0, 0.55),
                                ship("red-13", "red", -15, -320, Math.PI / 2, ENGINE_STOP, 0, 0.65),
                                ship("red-14", "red", 35, -300, Math.PI / 2, ENGINE_STOP, 0, 0.75),
                                ship("red-15", "red", 155, -300, Math.PI / 2, ENGINE_STOP, 0, 0.85)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-1", "blue", 205, -540, -Math.PI / 2, ENGINE_STOP, 0, 0),
                                ship("blue-2", "blue", 225, -500, -Math.PI / 2, ENGINE_STOP, 0, 0.1),
                                ship("blue-3", "blue", 190, -480, -Math.PI / 2, ENGINE_STOP, 0, 0.2),
                                ship("blue-4", "blue", 245, -420, -Math.PI / 2, ENGINE_STOP, 0, 0.3),
                                ship("blue-5", "blue", 240, -405, -Math.PI / 2, ENGINE_STOP, 0, 0.4),
                                ship("blue-6", "blue", 260, -340, -Math.PI / 2, ENGINE_STOP, 0, 0.5),
                                ship("blue-7", "blue", 65, -520, -Math.PI / 2, ENGINE_STOP, 0, 0.15),
                                ship("blue-8", "blue", 90, -480, -Math.PI / 2, ENGINE_STOP, 0, 0.25),
                                ship("blue-9", "blue", 50, -440, -Math.PI / 2, ENGINE_STOP, 0, 0.35),
                                ship("blue-10", "blue", 100, -400, -Math.PI / 2, ENGINE_STOP, 0, 0.45),
                                ship("blue-11", "blue", 60, -360, -Math.PI / 2, ENGINE_STOP, 0, 0.55),
                                ship("blue-12", "blue", 120, -320, -Math.PI / 2, ENGINE_STOP, 0, 0.65),
                                ship("blue-13", "blue", 170, -320, -Math.PI / 2, ENGINE_STOP, 0, 0.75),
                                ship("blue-14", "blue", 285, -300, -Math.PI / 2, ENGINE_STOP, 0, 0.85),
                                ship("blue-15", "blue", 220, -280, -Math.PI / 2, ENGINE_STOP, 0, 0.95)
                        ))
                ),
                List.of(new Vector2(0, -2100), new Vector2(70, -540), new Vector2(205, -540))
        );
    }

    private GameSetup escortDebugSetup() {
        WorldMap worldMap = worldMapService.denseWorld();
        List<ShipSetup> lightShips = new ArrayList<>(List.of(
                ship("light-1", TEAM_LIGHT, -180, -520, -0.58, ENGINE_SLOW, 0, 3),
                ship("light-2", TEAM_LIGHT, -260, -420, -0.42, ENGINE_SLOW, 4, 3),
                ship("light-3", TEAM_LIGHT, -40, -480, -0.75, ENGINE_SLOW, -4, 3)
        ));
        lightShips.addAll(specialScoutPlanes(TEAM_LIGHT, 0));
        List<ShipSetup> darkShips = new ArrayList<>(List.of(
                ship("dark-1", TEAM_DARK, 543, 208, 3.0, ENGINE_STOP, 0, 12),
                ship("dark-2", TEAM_DARK, 1010, 260, -2.95, ENGINE_STOP, 0, 14),
                ship("dark-3", TEAM_DARK, 1128, -773, -2.4, ENGINE_STOP, 0, 16)
        ));
        darkShips.addAll(specialScoutPlanes(TEAM_DARK, 0));
        return new GameSetup(
                "escort-debug",
                worldMap,
                List.of(
                        new FleetSetup(TEAM_LIGHT, lightShips),
                        new FleetSetup(TEAM_DARK, darkShips)
                ),
                denseRespawnCandidates()
        );
    }

    private GameSetup denseLandSetup() {
        return denseLandSetup(activeTeamIds(List.of()));
    }

    private GameSetup denseLandSetup(List<String> activeTeamIds) {
        return new GameSetup(
                "dense-land",
                worldMapService.denseWorld(),
                createDenseFleets(activeTeamIds),
                denseRespawnCandidates()
        );
    }

    private GameSetup scoutPlaneSetup(List<String> activeTeamIds) {
        return new GameSetup(
                "scout-plane",
                worldMapService.denseWorld(),
                createDenseFleets(activeTeamIds),
                denseRespawnCandidates()
        );
    }

    private GameSetup landmarkTourSetup() {
        List<ShipSetup> lightShips = new ArrayList<>(List.of(
                ship("light-1", TEAM_LIGHT, -460, -560, -0.35, "bot", ENGINE_STOP, 0, 99)
        ));
        lightShips.addAll(specialScoutPlanes(TEAM_LIGHT, 2));
        return new GameSetup(
                "landmark-tour",
                worldMapService.denseWorld(),
                List.of(
                        new FleetSetup(TEAM_LIGHT, lightShips),
                        new FleetSetup(TEAM_DARK, specialScoutPlanes(TEAM_DARK, 2))
                ),
                denseRespawnCandidates()
        );
    }

    private GameSetup fleetClashSetup() {
        List<Vector2> westboundPositions = fleetClashWestboundPositions();
        List<Vector2> eastboundPositions = fleetClashEastboundPositions();
        return new GameSetup(
                "dense-land-crowded",
                worldMapService.denseWorld(),
                List.of(
                        new FleetSetup(TEAM_DARK, createScenarioShips(TEAM_DARK, westboundPositions, -Math.PI / 2)),
                        new FleetSetup(TEAM_LIGHT, createScenarioShips(TEAM_LIGHT, eastboundPositions, Math.PI / 2))
                ),
                denseRespawnCandidates()
        );
    }

    private GameSetup fleetClashReverseSetup() {
        List<Vector2> westboundPositions = fleetClashWestboundPositions();
        List<Vector2> eastboundPositions = fleetClashEastboundPositions();
        return new GameSetup(
                "dense-land-crowded-reverse",
                worldMapService.denseWorld(),
                List.of(
                        new FleetSetup(TEAM_LIGHT, createScenarioShips(TEAM_LIGHT, westboundPositions, -Math.PI / 2)),
                        new FleetSetup(TEAM_DARK, createScenarioShips(TEAM_DARK, eastboundPositions, Math.PI / 2))
                ),
                denseRespawnCandidates()
        );
    }

    boolean isKnownTeam(String teamId) {
        return TEAM_ORDER.contains(teamId);
    }

    boolean isPublicTeam(String teamId) {
        return BASE_TEAMS.contains(teamId);
    }

    private static ShipSetup ship(String id, String teamId, double x, double z, double heading, int engineOrder,
                                  int rudderDegrees, double nextFireDelaySeconds) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                "bot",
                engineOrder,
                rudderDegrees,
                nextFireDelaySeconds,
                VEHICLE_TORPEDO_BOAT,
                0
        );
    }

    private static ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                                  int engineOrder, int rudderDegrees, double nextFireDelaySeconds) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                controlledBy,
                engineOrder,
                rudderDegrees,
                nextFireDelaySeconds,
                VEHICLE_TORPEDO_BOAT,
                0
        );
    }

    private static ShipSetup scoutPlane(String id, String teamId, double x, double z, double heading,
                                        int engineOrder, int rudderDegrees, double nextFireDelaySeconds) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                "bot",
                engineOrder,
                rudderDegrees,
                nextFireDelaySeconds,
                VEHICLE_SCOUT_PLANE,
                SCOUT_PLANE_START_Y
        );
    }

    private static List<ShipSetup> specialScoutPlanes(String teamId, int idOffset) {
        if (TEAM_LIGHT.equals(teamId)) {
            return List.of(
                    scoutPlane(teamId + "-F" + (idOffset + 4), teamId, -413, 82, -0.1, ENGINE_HALF, 5, 4),
                    scoutPlane(teamId + "-F" + (idOffset + 9), teamId, -1235, 395, -0.25, ENGINE_SLOW, -4, 7),
                    scoutPlane(teamId + "-F" + (idOffset + 14), teamId, -1441, -303, 0.48, ENGINE_HALF, 5, 10)
            );
        }
        if (TEAM_DARK.equals(teamId)) {
            return List.of(
                    scoutPlane(teamId + "-F" + (idOffset + 4), teamId, 543, 208, 3.0, ENGINE_HALF, -4, 4),
                    scoutPlane(teamId + "-F" + (idOffset + 9), teamId, 1525, 820, 2.8, ENGINE_SLOW, 4, 7),
                    scoutPlane(teamId + "-F" + (idOffset + 14), teamId, 1260, -180, -2.7, ENGINE_HALF, -5, 10)
            );
        }
        return List.of();
    }

    private static List<ShipSetup> createShips(String teamId, double[][] formation) {
        List<ShipSetup> ships = new ArrayList<>();
        for (int index = 0; index < formation.length; index += 1) {
            double[] slot = formation[index];
            String vehicleType = isBotScoutPlaneSlot(teamId, index) ? VEHICLE_SCOUT_PLANE : VEHICLE_TORPEDO_BOAT;
            String shipId = teamId + "-" + vehiclePrefix(vehicleType) + (index + 1);
            ships.add(new ShipSetup(
                    shipId,
                    teamId,
                    new Vector2(slot[0], slot[1]),
                    MathSupport.normalizeAngle(slot[2]),
                    "bot",
                    (int) slot[3],
                    (int) slot[4],
                    index == 0 ? 0 : 3 + index * 1.5,
                    vehicleType,
                    VEHICLE_SCOUT_PLANE.equals(vehicleType) ? SCOUT_PLANE_START_Y : 0
            ));
        }
        return ships;
    }

    private static String vehiclePrefix(String vehicleType) {
        return VEHICLE_SCOUT_PLANE.equals(vehicleType) ? "F" : "S";
    }

    private static boolean isBotScoutPlaneSlot(String teamId, int index) {
        return (TEAM_LIGHT.equals(teamId) || TEAM_DARK.equals(teamId)) && index == 3;
    }

    private static List<ShipSetup> createScenarioShips(String teamId, List<Vector2> positions, double heading) {
        List<ShipSetup> ships = new ArrayList<>();
        for (int index = 0; index < positions.size(); index += 1) {
            Vector2 position = positions.get(index);
            String vehicleType = isBotScoutPlaneSlot(teamId, index) ? VEHICLE_SCOUT_PLANE : VEHICLE_TORPEDO_BOAT;
            ships.add(new ShipSetup(
                    teamId + "-" + vehiclePrefix(vehicleType) + (index + 1),
                    teamId,
                    position,
                    MathSupport.normalizeAngle(heading),
                    "bot",
                    ENGINE_STOP,
                    0,
                    2 + index * 0.35,
                    vehicleType,
                    VEHICLE_SCOUT_PLANE.equals(vehicleType) ? SCOUT_PLANE_START_Y : 0
            ));
        }
        return ships;
    }

    private static List<FleetSetup> createDenseFleets(List<String> activeTeamIds) {
        List<List<double[]>> slotsByTeam = new ArrayList<>();
        activeTeamIds.forEach(ignored -> slotsByTeam.add(new ArrayList<>()));
        List<double[]> slots = denseFormationSlots();
        for (int index = 0; index < slots.size(); index += 1) {
            slotsByTeam.get(index % activeTeamIds.size()).add(slots.get(index));
        }

        List<FleetSetup> fleets = new ArrayList<>();
        for (int index = 0; index < activeTeamIds.size(); index += 1) {
            String teamId = activeTeamIds.get(index);
            fleets.add(new FleetSetup(teamId, createShips(teamId, slotsByTeam.get(index).toArray(double[][]::new))));
        }
        return fleets;
    }

    private static List<String> activeTeamIds(List<String> requestedTeamIds) {
        Set<String> activeTeams = new LinkedHashSet<>(BASE_TEAMS);
        TEAM_ORDER.stream()
                .filter(team -> requestedTeamIds != null && requestedTeamIds.contains(team))
                .forEach(activeTeams::add);
        return List.copyOf(activeTeams);
    }

    private static List<double[]> denseFormationSlots() {
        List<double[]> slots = new ArrayList<>();
        for (double[] slot : denseRedFormation()) {
            slots.add(slot);
        }
        for (double[] slot : denseBlueFormation()) {
            slots.add(slot);
        }
        return slots;
    }

    private static List<Vector2> fleetClashWestboundPositions() {
        return List.of(
                new Vector2(731, -664),
                new Vector2(724, -544),
                new Vector2(626, -394),
                new Vector2(570, -270),
                new Vector2(645, -150),
                new Vector2(754, -64),
                new Vector2(645, -19),
                new Vector2(855, -799),
                new Vector2(544, -735),
                new Vector2(390, -637),
                new Vector2(514, -877),
                new Vector2(379, -780),
                new Vector2(728, -259),
                new Vector2(968, -247)
        );
    }

    private static List<Vector2> fleetClashEastboundPositions() {
        return List.of(
                new Vector2(-450, -634),
                new Vector2(-334, -547),
                new Vector2(-232, -420),
                new Vector2(-154, -300),
                new Vector2(-202, -195),
                new Vector2(-390, -187),
                new Vector2(-195, -79),
                new Vector2(-7, -19),
                new Vector2(105, 86),
                new Vector2(229, 139),
                new Vector2(218, 236),
                new Vector2(195, 311),
                new Vector2(-326, -656),
                new Vector2(-97, -679),
                new Vector2(-206, -746)
        );
    }

    private static double[][] redFormation() {
        return new double[][]{
                {96, -340, -2.32, ENGINE_STOP, 0},
                {130, -472, -2.55, ENGINE_SLOW, -4},
                {-40, -300, -2.0, ENGINE_SLOW, 5},
                {220, -500, -2.5, ENGINE_HALF, 4},
                {-260, 1500, 2.8, ENGINE_SLOW, -7},
                {1420, 760, -2.4, ENGINE_HALF, 5},
                {520, -1180, -2.1, ENGINE_HALF, -6},
                {-360, -1160, 0.72, ENGINE_SLOW, 5},
                {1780, -820, -2.55, ENGINE_TWO_THIRDS, -7},
                {-1260, 1620, 2.15, ENGINE_SLOW, 6},
                {1040, 1180, -2.65, ENGINE_HALF, -5},
                {-920, 920, 1.25, ENGINE_SLOW, 6},
                {1880, 160, -2.35, ENGINE_HALF, 4},
                {-1460, -820, 0.62, ENGINE_HALF, -6},
                {980, -1680, -2.05, ENGINE_SLOW, 5}
        };
    }

    private static double[][] blueFormation() {
        return new double[][]{
                {-560, -520, 0.9, ENGINE_STOP, 0},
                {-310, -240, 1.45, ENGINE_SLOW, -5},
                {-940, -760, 0.65, ENGINE_HALF, 7},
                {-560, -650, 0.75, ENGINE_HALF, -5},
                {1120, 420, 2.7, ENGINE_SLOW, 7},
                {-420, -760, -0.4, ENGINE_HALF, 5},
                {-860, -1480, -0.45, ENGINE_SLOW, -8},
                {1120, -1280, -2.2, ENGINE_HALF, -6},
                {470, 900, -2.8, ENGINE_TWO_THIRDS, 8},
                {-760, 1040, -2.55, ENGINE_HALF, -5},
                {1580, 80, 2.95, ENGINE_SLOW, 4},
                {860, -920, -2.72, ENGINE_HALF, -6},
                {-1660, -1540, -0.75, ENGINE_HALF, 5},
                {-980, 60, -1.3, ENGINE_SLOW, -7},
                {650, -72, -2.58, ENGINE_TWO_THIRDS, 6}
        };
    }

    private static List<Vector2> respawnCandidates() {
        return List.of(
                new Vector2(83, 1448),
                new Vector2(420, 1043),
                new Vector2(218, 660),
                new Vector2(945, 1118),
                new Vector2(728, 683),
                new Vector2(-502, 398),
                new Vector2(-45, 248),
                new Vector2(630, 203),
                new Vector2(540, 308),
                new Vector2(1485, 53),
                new Vector2(1065, -172),
                new Vector2(953, -1050),
                new Vector2(135, -570),
                new Vector2(90, -292),
                new Vector2(-840, -247),
                new Vector2(-1485, -135),
                new Vector2(-1260, -892),
                new Vector2(-1492, -1072),
                new Vector2(-555, -1267),
                new Vector2(-547, -1252),
                new Vector2(-240, 1020),
                new Vector2(-285, 1088),
                new Vector2(-255, 1060),
                new Vector2(548, -975),
                new Vector2(1598, -937),
                new Vector2(-885, 1328),
                new Vector2(-1434, 1048),
                new Vector2(-1192, 548),
                new Vector2(-30, -15)
        );
    }

    private static List<Vector2> previousRespawnCandidates() {
        return List.of(
                new Vector2(96, -340),
                new Vector2(300, -70),
                new Vector2(-940, -760),
                new Vector2(-455, -155),
                new Vector2(182, 987),
                new Vector2(1420, 760),
                new Vector2(520, -1180),
                new Vector2(-860, -1480),
                new Vector2(1120, -1280),
                new Vector2(470, 900),
                new Vector2(-760, 1040),
                new Vector2(1580, 80),
                new Vector2(-1460, -820),
                new Vector2(980, -1680),
                new Vector2(-1260, 1620),
                new Vector2(1880, 160),
                new Vector2(-980, 60),
                new Vector2(1040, 1180)
        );
    }

    private static double[][] denseRedFormation() {
        return new double[][]{
                {-180, -520, -0.58, ENGINE_STOP, 0},
                {-260, -420, -0.42, ENGINE_SLOW, 4},
                {-40, -480, -0.75, ENGINE_SLOW, -4},
                {-413, 82, -0.1, ENGINE_HALF, 5},
                {-210, 240, -0.32, ENGINE_SLOW, -5},
                {-340, 520, -0.9, ENGINE_HALF, 4},
                {-961, 133, 0.35, ENGINE_SLOW, -6},
                {-1260, -220, 0.15, ENGINE_HALF, 5},
                {-1235, 395, -0.25, ENGINE_SLOW, -4},
                {-1173, -826, 0.75, ENGINE_HALF, 6},
                {-488, -1080, 0.62, ENGINE_SLOW, -5},
                {92, -671, -0.9, ENGINE_HALF, 4},
                {205, 365, -1.18, ENGINE_SLOW, -6},
                {-1441, -303, 0.48, ENGINE_HALF, 5},
                {429, 216, -1.4, ENGINE_SLOW, -4}
        };
    }

    private static double[][] denseBlueFormation() {
        return new double[][]{
                {370, -335, 2.55, ENGINE_STOP, 0},
                {485, -203, 2.72, ENGINE_SLOW, -5},
                {195, -505, 2.35, ENGINE_SLOW, 5},
                {543, 208, 3.0, ENGINE_HALF, -4},
                {369, 248, 2.82, ENGINE_SLOW, 6},
                {663, 265, 2.35, ENGINE_HALF, -5},
                {1010, 260, -2.95, ENGINE_SLOW, 5},
                {1340, -190, -2.92, ENGINE_HALF, -5},
                {1525, 820, 2.8, ENGINE_SLOW, 4},
                {1128, -773, -2.4, ENGINE_HALF, -6},
                {560, -1120, -2.25, ENGINE_SLOW, 5},
                {20, -995, 2.2, ENGINE_HALF, -4},
                {-50, 430, 2.05, ENGINE_SLOW, 5},
                {1260, -180, -2.7, ENGINE_HALF, -5},
                {-720, -360, 1.65, ENGINE_SLOW, 4}
        };
    }

    private static List<Vector2> denseRespawnCandidates() {
        return List.of(
                new Vector2(83, 1448),
                new Vector2(420, 1043),
                new Vector2(218, 660),
                new Vector2(945, 1118),
                new Vector2(728, 683),
                new Vector2(-502, 398),
                new Vector2(-45, 248),
                new Vector2(630, 203),
                new Vector2(540, 308),
                new Vector2(1485, 53),
                new Vector2(1065, -172),
                new Vector2(953, -1050),
                new Vector2(135, -570),
                new Vector2(90, -292),
                new Vector2(-840, -247),
                new Vector2(-1485, -135),
                new Vector2(-1260, -892),
                new Vector2(-1492, -1072),
                new Vector2(-555, -1267),
                new Vector2(-547, -1252),
                new Vector2(-240, 1020),
                new Vector2(-285, 1088),
                new Vector2(-255, 1060),
                new Vector2(548, -975),
                new Vector2(1598, -937),
                new Vector2(-885, 1328)
        );
    }

    private static List<Vector2> previousDenseRespawnCandidates() {
        return List.of(
                new Vector2(-180, -520),
                new Vector2(370, -335),
                new Vector2(-413, 82),
                new Vector2(543, 208),
                new Vector2(-210, 240),
                new Vector2(369, 248),
                new Vector2(-340, 520),
                new Vector2(663, 265),
                new Vector2(-961, 133),
                new Vector2(1010, 260),
                new Vector2(-1260, -220),
                new Vector2(1340, -190),
                new Vector2(-1173, -826),
                new Vector2(1128, -773),
                new Vector2(92, -671),
                new Vector2(20, -995),
                new Vector2(429, 216),
                new Vector2(-720, -360)
        );
    }

    private interface RespawnLandDistance {
        double distanceFromLand(Vector2 position);
    }

    private record PolygonRespawnLandDistance(List<Point2> polygon) implements RespawnLandDistance {
        private PolygonRespawnLandDistance {
            polygon = List.copyOf(polygon);
        }

        @Override
        public double distanceFromLand(Vector2 position) {
            return polygonDistance(position, polygon);
        }
    }

    private record EllipseRespawnLandDistance(Landmass landmass) implements RespawnLandDistance {
        @Override
        public double distanceFromLand(Vector2 position) {
            if (LandGeometry.isInLandWater(position, landmass)) {
                return 0;
            }
            double normalizedWaterDistance = LandGeometry.shapeDistance(position, landmass)
                    - LandGeometry.navigationBlockDistance(landmass);
            return normalizedWaterDistance * Math.max(1, Math.min(landmass.rx(), landmass.rz()));
        }
    }

    private record CandidateScan(List<Vector2> candidates, int testedPoints) {
        private CandidateScan {
            candidates = List.copyOf(candidates);
        }
    }

    private record Bounds(double minX, double minZ, double maxX, double maxZ) {
    }
}
