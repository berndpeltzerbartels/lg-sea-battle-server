package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    private final RadarService radarService = new RadarService();
    private final NavigationService navigationService = new NavigationService();

    @Test
    void usesInjectedWorldAndFleets() {
        GameSetup setup = new GameSetup(
                "tiny-test",
                new WorldMap(9001, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", -10, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 10, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-10, 0), new Vector2(10, 0))
        );

        GameSession session = new GameSession(setup);
        GameSnapshot snapshot = session.snapshot();

        assertEquals("tiny-test", snapshot.sessionId());
        assertEquals(9001, session.worldMap().version());
        assertEquals(2, snapshot.ships().size());
        assertEquals("red-1", snapshot.ships().get(0).id());
        assertEquals("blue-1", snapshot.ships().get(1).id());
    }

    @Test
    void sideRamSinksTargetAndStopsAttacker() {
        GameSession session = new GameSession(new GameSetup(
                "ram-test",
                new WorldMap(9002, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 24);

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertNotNull(attacker);
        assertNotNull(target);
        assertEquals("active", attacker.state());
        assertEquals(0.0, attacker.speed(), 0.001);
        assertEquals(2, attacker.engineOrder());
        assertEquals("sunk", target.state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void defaultFactoryCanSelectSmallTestSetups() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());

        GameSetup singleIsland = factory.setup("single-island");
        assertEquals("single-island", singleIsland.id());
        assertEquals(1001, singleIsland.worldMap().version());
        assertEquals(1, singleIsland.worldMap().landmasses().size());
        assertEquals(2, singleIsland.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());

        GameSetup ramSide = factory.setup("ram-side");
        assertEquals("ram-side", ramSide.id());
        assertEquals(1002, ramSide.worldMap().version());
        assertEquals(2, ramSide.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());

        GameSetup explosionDemo = factory.setup("explosion-demo");
        assertEquals("explosion-demo", explosionDemo.id());
        assertEquals(30, explosionDemo.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
    }

    @Test
    void defaultSetupPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
    }

    @Test
    void explosionDemoPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).setup("explosion-demo"));
    }

    @Test
    void asternMovementCanLeaveBowGroundingWhenSternIsClear() {
        WorldMap worldMap = new WorldMap(9003, List.of(testIsland("bow-rock", 0, 5.2, 2.5, 2.5)));
        Ship ship = new Ship("red-1", "red", new Vector2(0, 0), 0, "player-test");

        assertTrue(navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap));
        assertFalse(navigationService.isShipMovementBlocked(ship.position(), ship.heading(), -1, worldMap));

        ship.applyCommand(0, 0);
        for (int i = 0; i < 40; i += 1) {
            ship.update(0.05, navigationService, worldMap);
        }

        assertTrue(ship.position().z() < -0.3);
    }

    @Test
    void asternMovementStaysBlockedWhenSternIsGrounded() {
        WorldMap worldMap = new WorldMap(9004, List.of(testIsland("stern-rock", 0, -1.05, 1.9, 1.9)));
        Ship ship = new Ship("red-1", "red", new Vector2(0, 0), 0, "player-test");

        assertTrue(navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap));
        assertTrue(navigationService.isShipMovementBlocked(ship.position(), ship.heading(), -1, worldMap));
    }

    private void assertSetupPlacesShipsAndRespawnsInNavigableWater(GameSetup setup) {
        List<String> blockedPositions = new java.util.ArrayList<>();
        setup.fleets().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> navigationService.isShipBlocked(ship.position(), ship.heading(), setup.worldMap()))
                .map(ship -> ship.id() + " starts blocked at " + ship.position()
                        + " nearest " + nearestNavigable(setup.worldMap(), ship.position(), ship.heading()))
                .forEach(blockedPositions::add);

        setup.respawnCandidates().stream()
                .filter(candidate -> navigationService.isShipBlocked(candidate, 0, setup.worldMap()))
                .map(candidate -> "Respawn candidate is blocked at " + candidate
                        + " nearest " + nearestNavigable(setup.worldMap(), candidate, 0))
                .forEach(blockedPositions::add);

        assertTrue(blockedPositions.isEmpty(), String.join("\n", blockedPositions));
    }

    private Vector2 nearestNavigable(WorldMap worldMap, Vector2 origin, double heading) {
        Vector2 best = origin;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int radius = 20; radius <= 360; radius += 20) {
            for (int step = 0; step < 32; step += 1) {
                double angle = step * Math.PI * 2 / 32.0;
                Vector2 candidate = origin.add(new Vector2(Math.cos(angle) * radius, Math.sin(angle) * radius));
                if (!navigationService.isShipBlocked(candidate, heading, worldMap)) {
                    double distance = origin.distanceTo(candidate);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (bestDistance < Double.POSITIVE_INFINITY) {
                return best;
            }
        }
        return best;
    }

    private Landmass testIsland(String name, double x, double z, double rx, double rz) {
        return new Landmass(
                "island",
                name,
                x,
                z,
                rx,
                rz,
                rx,
                rz,
                rx * 1.2,
                rz * 1.2,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private GameSnapshot tickUntilShipState(GameSession session, String shipId, String state, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot ship = findShip(snapshot, shipId);
            if (ship != null && state.equals(ship.state())) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private ShipSnapshot findShip(GameSnapshot snapshot, String shipId) {
        return snapshot.ships().stream()
                .filter(ship -> shipId.equals(ship.id()))
                .findFirst()
                .orElse(null);
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, int engineOrder, int rudderDegrees) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                "scenario",
                engineOrder,
                rudderDegrees,
                99
        );
    }
}
