package one.xis.seabattle;

import one.xis.context.Service;

import java.util.ArrayList;
import java.util.List;

@Service
final class DefaultGameSetupFactory {

    private static final int ENGINE_STOP = 2;
    private static final int ENGINE_SLOW = 3;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;

    private final WorldMapService worldMapService;

    DefaultGameSetupFactory(WorldMapService worldMapService) {
        this.worldMapService = worldMapService;
    }

    GameSetup setup(String setupId) {
        if (setupId == null || setupId.isBlank() || "default".equals(setupId)) {
            return defaultSetup();
        }
        return switch (setupId) {
            case "single-island" -> singleIslandSetup();
            case "ram-side" -> ramSideSetup();
            case "explosion-demo" -> explosionDemoSetup();
            default -> throw new IllegalArgumentException("Unknown game setup: " + setupId);
        };
    }

    GameSetup defaultSetup() {
        return new GameSetup(
                "local-test",
                worldMapService.world(),
                List.of(
                        new FleetSetup("red", createShips("red", redFormation())),
                        new FleetSetup("blue", createShips("blue", blueFormation()))
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
                                ship("blue-5", "blue", 221, -397, -Math.PI / 2, ENGINE_STOP, 0, 0.4),
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
                nextFireDelaySeconds
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
                nextFireDelaySeconds
        );
    }

    private static List<ShipSetup> createShips(String teamId, double[][] formation) {
        List<ShipSetup> ships = new ArrayList<>();
        for (int index = 0; index < formation.length; index += 1) {
            double[] slot = formation[index];
            ships.add(new ShipSetup(
                    teamId + "-" + (index + 1),
                    teamId,
                    new Vector2(slot[0], slot[1]),
                    MathSupport.normalizeAngle(slot[2]),
                    "bot",
                    (int) slot[3],
                    (int) slot[4],
                    index == 0 ? 0 : 3 + index * 1.5
            ));
        }
        return ships;
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
}
