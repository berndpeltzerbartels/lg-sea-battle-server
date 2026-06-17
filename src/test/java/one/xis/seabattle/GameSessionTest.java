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
    void steepSideRamAtEightyDegreesSinksTargetOnly() {
        double attackerHeading = Math.toRadians(80);
        Vector2 targetPosition = Vector2.fromHeading(attackerHeading).scale(56);
        GameSession session = new GameSession(new GameSetup(
                "steep-side-ram-test",
                new WorldMap(9011, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, attackerHeading, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", targetPosition.x(), targetPosition.z(), 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), targetPosition)
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 30);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
    }

    @Test
    void shallowAngleRamChangesCourseWithoutSinkingShips() {
        double attackerHeading = Math.toRadians(8);
        Vector2 targetPosition = Vector2.fromHeading(attackerHeading).scale(56);
        GameSession session = new GameSession(new GameSetup(
                "shallow-angle-ram-test",
                new WorldMap(9012, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, attackerHeading, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", targetPosition.x(), targetPosition.z(), 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), targetPosition)
        ));

        GameSnapshot snapshot = tickUntilShipsTouch(session, 30);

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertEquals("active", attacker.state());
        assertEquals("active", target.state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
        assertTrue(Math.abs(MathSupport.normalizeAngle(attacker.heading() - attackerHeading)) > Math.toRadians(2));
        assertTrue(Math.abs(MathSupport.normalizeAngle(target.heading())) > Math.toRadians(2));
    }

    @Test
    void playerSideRamEnemyScoresKillWithoutSinkingAttacker() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-ram-score-test",
                new WorldMap(9007, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "bot", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "blue-1", "sunk", new Vector2(0, 0), Math.PI / 2, 24
        );

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(1, snapshot.killsByPlayer().get(playerId));
    }

    @Test
    void playerSideRamFriendlyShipSubtractsScore() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-friendly-ram-score-test",
                new WorldMap(9008, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, Math.PI / 2, "bot", 5, 0),
                                ship("red-2", "red", 56, 0, 0, 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 240, 240, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0), new Vector2(240, 240))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "red-2", "sunk", new Vector2(0, 0), Math.PI / 2, 24
        );

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "red-2").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(-2, snapshot.killsByPlayer().get(playerId));
    }

    @Test
    void playerLosesThreePointsWhenSunk() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-sunk-score-test",
                new WorldMap(9014, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, playerId, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 24);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(-3, snapshot.killsByPlayer().get(playerId));
    }

    @Test
    void diagonalPlayerSideRamEnemyScoresKillWithoutSinkingAttacker() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-diagonal-ram-score-test",
                new WorldMap(9009, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 4, playerId, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 41, 41, 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(41, 41))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "blue-1", "sunk", new Vector2(0, 0), Math.PI / 4, 30
        );

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.killsByPlayer().get(playerId));
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
        assertEquals(List.of("dark", "light"),
                denseLand.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(15, 15), denseLand.fleets().stream().map(fleet -> fleet.ships().size()).toList());
    }

    @Test
    void denseLandAddsAdditionalPartiesOnlyForRequestedHumanTeams() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());

        GameSetup threeTeams = factory.setup("dense-land", List.of("green"));
        assertEquals(List.of("dark", "light", "green"),
                threeTeams.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(10, 10, 10), threeTeams.fleets().stream().map(fleet -> fleet.ships().size()).toList());

        GameSetup fourTeams = factory.setup("dense-land", List.of("green", "sand"));
        assertEquals(List.of("dark", "light", "green", "sand"),
                fourTeams.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(8, 8, 7, 7), fourTeams.fleets().stream().map(fleet -> fleet.ships().size()).toList());
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
    void denseLandHasCalculatedBeachBandThatBlocksNavigationButNotRadar() {
        WorldMap worldMap = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land").worldMap();
        List<String> navigationMismatches = new java.util.ArrayList<>();
        List<String> radarMismatches = new java.util.ArrayList<>();

        for (Landmass landmass : worldMap.landmasses()) {
            sampleBoundaryPoints(landmass).stream()
                    .forEach(point -> {
                        double distance = LandGeometry.shapeDistance(point, landmass);
                        boolean landWater = LandGeometry.isInLandWater(point, landmass);
                        boolean navigationBlocked = LandGeometry.isBlockedByLandmass(point, landmass);
                        boolean radarBlocked = LandGeometry.isRadarBlockedByLandmass(point, landmass);
                        boolean expectedNavigation = distance < LandGeometry.navigationBlockDistance(landmass) && !landWater;
                        boolean expectedRadar = distance < LandGeometry.radarBlockDistance(landmass) && !landWater;
                        if (navigationBlocked != expectedNavigation) {
                            navigationMismatches.add(landmass.name() + " has wrong navigation boundary at " + point
                                    + " distance=" + distance + " expected=" + expectedNavigation
                                    + " actual=" + navigationBlocked);
                        }
                        if (radarBlocked != expectedRadar) {
                            radarMismatches.add(landmass.name() + " has wrong radar boundary at " + point
                                    + " distance=" + distance + " expected=" + expectedRadar
                                    + " actual=" + radarBlocked);
                        }
                    });
        }

        assertTrue(navigationMismatches.isEmpty(), String.join("\n", navigationMismatches.stream().limit(40).toList()));
        assertTrue(radarMismatches.isEmpty(), String.join("\n", radarMismatches.stream().limit(40).toList()));

        Landmass coastline = worldMap.landmasses().stream()
                .filter(landmass -> "coastline".equals(landmass.kind()))
                .findFirst()
                .orElseThrow();
        double beachBandDistance = (LandGeometry.navigationBlockDistance(coastline) + LandGeometry.radarBlockDistance(coastline)) / 2;
        Vector2 beachBandPoint = new Vector2(coastline.x() + coastline.rx() * beachBandDistance, coastline.z());
        assertTrue(LandGeometry.isBlockedByLandmass(beachBandPoint, coastline));
        assertFalse(LandGeometry.isRadarBlockedByLandmass(beachBandPoint, coastline));
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

    @Test
    void respawnSelectionUsesCandidatesAsRoundRobin() {
        List<Vector2> candidates = List.of(
                new Vector2(-200, 0),
                new Vector2(0, 0),
                new Vector2(200, 0)
        );
        GameSession session = new GameSession(new GameSetup(
                "respawn-round-robin-test",
                new WorldMap(9012, List.of()),
                List.of(),
                candidates
        ));
        Ship sunkShip = new Ship("red-1", "red", new Vector2(0, -500), 0, "bot");

        assertEquals(candidates.get(0), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(1), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(2), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(0), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
    }

    @Test
    void respawnSelectionDoesNotRepeatLastCandidateWhenAllCandidatesAreNegative() {
        List<Vector2> candidates = List.of(
                new Vector2(-500, 0),
                new Vector2(0, 0),
                new Vector2(500, 0)
        );
        GameSession session = new GameSession(new GameSetup(
                "respawn-negative-round-robin-test",
                new WorldMap(9013, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-a", "red", -500, 0, 0, 2, 0),
                        ship("red-b", "red", 0, 0, 0, 2, 0),
                        ship("red-c", "red", 500, 0, 0, 2, 0)
                ))),
                candidates
        ));
        Ship sunkShip = new Ship("red-1", "red", new Vector2(0, -500), 0, "bot");

        Vector2 first = session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService);
        Vector2 second = session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService);

        assertEquals(candidates.get(0), first);
        assertEquals(candidates.get(1), second);
    }

    @Test
    void defaultSetupHasEnoughRespawnCandidates() {
        GameSetup setup = new DefaultGameSetupFactory(new WorldMapService()).setup("default");

        assertTrue(setup.respawnCandidates().size() >= 10);
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

    private GameSnapshot tickUntilShipsTouch(GameSession session, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot red = findShip(snapshot, "red-1");
            ShipSnapshot blue = findShip(snapshot, "blue-1");
            if (red != null && blue != null && new Vector2(red.x(), red.z()).distanceTo(new Vector2(blue.x(), blue.z())) < 6) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private GameSnapshot tickPlayerForwardUntilShipState(GameSession session, String playerId, String teamId,
                                                         String shipId, String state, Vector2 startPosition,
                                                         double heading, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        double speed = 9.6;
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            Vector2 position = startPosition.add(Vector2.fromHeading(heading).scale(speed * elapsed));
            session.updatePlayerState(
                    new PlayerStateUpdate(playerId, teamId, position.x(), position.z(), heading, speed, 0, 7, 0, elapsed),
                    navigationService,
                    session.worldMap()
            );
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
        return ship(id, teamId, x, z, heading, "scenario", engineOrder, rudderDegrees);
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                           int engineOrder, int rudderDegrees) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                controlledBy,
                engineOrder,
                rudderDegrees,
                99
        );
    }
}
