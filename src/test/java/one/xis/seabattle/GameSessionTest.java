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
    void diagonalSideRamSinksTargetOnly() {
        GameSession session = new GameSession(new GameSetup(
                "diagonal-ram-test",
                new WorldMap(9003, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 4, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 41, 41, 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(41, 41))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 30);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
    }

    @Test
    void releasePlayerReturnsAssignedShipToBotControl() {
        GameSession session = new GameSession(new GameSetup(
                "release-test",
                new WorldMap(9005, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 40, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(40, 0))
        ));

        session.updatePlayerState(new PlayerStateUpdate("player-BP-test", "red", 0, 0, 0, 0, 0, 2, 0, 0), navigationService, session.worldMap());
        assertEquals("player-BP-test", findShip(session.snapshot(), "red-1").controlledBy());

        session.releasePlayer("player-BP-test");

        assertEquals("bot", findShip(session.snapshot(), "red-1").controlledBy());
    }

    @Test
    void playerStateUsesClientPositionWithoutServerAdvancingHumanShip() {
        GameSession session = new GameSession(new GameSetup(
                "client-authority-test",
                new WorldMap(9006, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 200, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(200, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "red", 15, 25, 0.7, 9.6, 0.12, 7, 14, 123.45),
                navigationService,
                session.worldMap()
        );
        ShipSnapshot afterClientUpdate = findShip(session.snapshot(), "red-1");
        assertEquals(15, afterClientUpdate.x(), 0.001);
        assertEquals(25, afterClientUpdate.z(), 0.001);
        assertEquals(0.7, afterClientUpdate.heading(), 0.001);
        assertEquals(9.6, afterClientUpdate.speed(), 0.001);
        assertEquals(0.12, afterClientUpdate.turnVelocity(), 0.001);

        session.update(0.5, new RadarService(), navigationService, session.worldMap());

        ShipSnapshot afterServerTick = findShip(session.snapshot(), "red-1");
        assertEquals(15, afterServerTick.x(), 0.001);
        assertEquals(25, afterServerTick.z(), 0.001);
        assertEquals(0.7, afterServerTick.heading(), 0.001);
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

        GameSetup denseLand = factory.setup("dense-land");
        assertEquals("dense-land", denseLand.id());
        assertEquals(9, denseLand.worldMap().version());
        assertEquals(30, denseLand.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
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
    void denseLandPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land"));
    }

    @Test
    void denseLandKeepsActiveShipsInNavigableWaterDuringSimulation() {
        GameSetup setup = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land");
        GameSession session = new GameSession(setup);

        for (int tick = 0; tick < 400; tick += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            assertActiveShipsNavigable("tick " + tick, session.snapshot(), session.worldMap());
        }
    }

    @Test
    void denseLandNavigationBoundaryMatchesVisibleLandBoundary() {
        WorldMap worldMap = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land").worldMap();
        List<String> mismatches = new java.util.ArrayList<>();

        for (Landmass landmass : worldMap.landmasses()) {
            if ("island".equals(landmass.kind())) {
                continue;
            }
            sampleBoundaryPoints(landmass).stream()
                    .filter(point -> !isInLandWater(point, landmass))
                    .forEach(point -> {
                        double visibleDistance = shapeDistance(point, landmass, landmass.rx(), landmass.rz());
                        double navigationDistance = shapeDistance(point, landmass, landmass.navigationRx(), landmass.navigationRz());
                        double blockDistance = blockDistance(landmass);
                        if (visibleDistance < blockDistance && navigationDistance >= blockDistance) {
                            mismatches.add(landmass.name() + " visible land is navigable at " + point
                                    + " visible=" + visibleDistance + " navigation=" + navigationDistance);
                        }
                        if (navigationDistance < blockDistance && visibleDistance >= blockDistance + 0.02) {
                            mismatches.add(landmass.name() + " invisible land blocks at " + point
                                    + " visible=" + visibleDistance + " navigation=" + navigationDistance);
                        }
                    });
        }

        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches.stream().limit(40).toList()));
    }

    @Test
    void denseLandVolcanoCenterIsNotNavigableWater() {
        WorldMap worldMap = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land").worldMap();

        assertTrue(navigationService.isShipBlocked(new Vector2(310, -201), 2.55, worldMap));
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

    private void assertActiveShipsNavigable(String label, GameSnapshot snapshot, WorldMap worldMap) {
        List<String> blockedPositions = new java.util.ArrayList<>();
        snapshot.ships().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> navigationService.isShipBlocked(new Vector2(ship.x(), ship.z()), ship.heading(), worldMap))
                .map(ship -> label + " " + ship.id() + " is blocked at "
                        + new Vector2(ship.x(), ship.z()) + " nearest "
                        + nearestNavigable(worldMap, new Vector2(ship.x(), ship.z()), ship.heading()))
                .forEach(blockedPositions::add);

        assertTrue(blockedPositions.isEmpty(), String.join("\n", blockedPositions));
    }

    private List<Vector2> sampleBoundaryPoints(Landmass landmass) {
        List<Vector2> points = new java.util.ArrayList<>();
        double minX = landmass.x() - landmass.rx() * 1.18;
        double maxX = landmass.x() + landmass.rx() * 1.18;
        double minZ = landmass.z() - landmass.rz() * 1.18;
        double maxZ = landmass.z() + landmass.rz() * 1.18;
        double step = Math.max(4, Math.min(18, Math.min(landmass.rx(), landmass.rz()) / 5));

        for (double x = minX; x <= maxX; x += step) {
            for (double z = minZ; z <= maxZ; z += step) {
                points.add(new Vector2(x, z));
            }
        }
        return points;
    }

    private double shapeDistance(Vector2 position, Landmass landmass, double rx, double rz) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        double nx = localX / rx;
        double nz = localZ / rz;
        double distance = Math.sqrt(nx * nx + nz * nz);
        if (!"coastline".equals(landmass.kind())) {
            return distance;
        }
        double angle = Math.atan2(nz, nx);
        return distance / coastRadiusFactor(angle, landmass);
    }

    private double blockDistance(Landmass landmass) {
        return "coastline".equals(landmass.kind()) ? 1.055 : 1;
    }

    private boolean isInLandWater(Vector2 position, Landmass landmass) {
        double localX = position.x() - landmass.x();
        double localZ = position.z() - landmass.z();
        return isInWaterway(localX, localZ, landmass)
                || isInLake(localX, localZ, landmass);
    }

    private boolean isInWaterway(double localX, double localZ, Landmass landmass) {
        return landmass.waterways().stream().anyMatch(waterway ->
                distanceToSegment(localX, localZ, waterway.from().x(), waterway.from().z(),
                        waterway.to().x(), waterway.to().z()) <= waterway.width() * 0.58
        );
    }

    private boolean isInLake(double localX, double localZ, Landmass landmass) {
        return landmass.lakes().stream().anyMatch(lake -> {
            double nx = (localX - lake.x()) / lake.rx();
            double nz = (localZ - lake.z()) / lake.rz();
            return nx * nx + nz * nz <= 1;
        });
    }

    private double fjordCarve(double localX, double localZ, double rx, double rz, Landmass landmass) {
        double carve = 0;
        for (Fjord fjord : landmass.fjords()) {
            double dirX = Math.sin(fjord.angle());
            double dirZ = Math.cos(fjord.angle());
            double along = (localX * dirX) / rx + (localZ * dirZ) / rz;
            double across = Math.abs((localX * dirZ) / rx - (localZ * dirX) / rz);
            double reach = fjord.reach();
            double width = fjord.width();
            double outerFade = 1 - MathSupport.smoothstep(1.02, 1.16, along);
            double innerFade = MathSupport.smoothstep(1 - reach, 1 - reach + 0.18, along);
            double channel = 1 - MathSupport.smoothstep(width * 0.45, width, across);
            carve = Math.max(carve, channel * outerFade * innerFade);
        }
        return carve;
    }

    private double coastRadiusFactor(double angle, Landmass landmass) {
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

    private int stableNameSeed(String name) {
        int seed = 0;
        for (int i = 0; i < name.length(); i += 1) {
            seed = (seed * 31 + name.charAt(i)) % 9973;
        }
        return seed;
    }

    private double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
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
