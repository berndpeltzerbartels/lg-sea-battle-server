package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubmarineTacticsTest {
    @Test
    void aircraftTriggersDiveAndLastSeenPositionExpiresUnderwater() {
        Vehicle sub = submarine();
        Vehicle plane = enemy(400);
        plane = plane.convertedTo("scout-plane");
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) sub, List.of(plane), 0);
        assertTrue(sub.isAtPeriscopeDepth());
        Vehicle nearPlane = enemy(100);
        nearPlane = nearPlane.convertedTo("scout-plane");
        assertEquals(new Vector2(0, 100), SubmarineBotCalculation.updateDiveAndEscape((Submarine) sub, List.of(nearPlane), 1));
        assertTrue(sub.isFullySubmerged());
        // No live tracking under water, even when the aircraft moves elsewhere.
        assertEquals(new Vector2(0, 100), SubmarineBotCalculation.updateDiveAndEscape((Submarine) sub, List.of(plane), 5));
        assertTrue(sub.isFullySubmerged());
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) sub, List.of(nearPlane), 11);
        assertTrue(sub.isOnSurface());
    }

    @Test
    void friendlyAircraftDoesNotTriggerDive() {
        Vehicle sub = submarine();
        Vehicle plane = new TorpedoBoat("friend", "light", new Vector2(0, 10), 0, "bot");
        plane = plane.convertedTo("scout-plane");
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) sub, List.of(plane), 0);
        assertTrue(sub.isOnSurface());
    }

    @Test
    void sessionIncludesAircraftInSubmarineThreatContacts() {
        GameSession session = new GameSession(new GameSetup("air-threat",
                new WorldMap(99009, List.of()), List.of(
                new FleetSetup("light", List.of(new VehicleSetup("sub", "light", new Vector2(0, 0),
                        0, "bot", 7, 0, 9999, "submarine", 0))),
                new FleetSetup("dark", List.of(new VehicleSetup("plane", "dark", new Vector2(0, 100),
                        0, "player", 2, 0, 9999, "scout-plane", 100)))), List.of()));
        session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
        assertEquals("submerged", session.snapshot().ships().stream()
                .filter(s -> s.id().equals("sub")).findFirst().orElseThrow().depthState());
        assertTrue(session.snapshot().torpedoes().isEmpty());
    }

    @Test
    void deeplySubmergedBotLimitsSpeedToSixAndRestoresSurfaceSpeed() {
        Vehicle sub = submarine();
        var navigation = new NavigationService();
        var world = new WorldMap(99009, List.of());
        sub.botDepth("submerged");
        sub.applyCommand(8, 0);
        sub.update(20, navigation, world);
        assertEquals(6, sub.speed(), .001);
        sub.applyCommand(0, 0);
        sub.update(20, navigation, world);
        assertEquals(-6, sub.speed(), .001);
        sub.botDepth("surface");
        sub.applyCommand(8, 0);
        sub.update(20, navigation, world);
        assertEquals(EngineOrders.speedFor(8), sub.speed(), .001);
    }
    @Test
    void periscopeAttackHoldsTheWholeBandAndReversesOnlyBelowItsMinimum() {
        var sub = new Submarine("sub", "light", new Vector2(0, 0), 0, "bot");
        Vehicle target = enemy(300);
        for (double distance : new double[]{250, 275, 300, 350, 400})
            assertEquals(GameRules.ENGINE_STOP,
                    SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, distance, 0));
        assertEquals(GameRules.ENGINE_ASTERN,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 249.99, 0));
        assertEquals(GameRules.ENGINE_STOP,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 250, 0),
                "No remembered reverse command may carry into the valid band");
        assertTrue(SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 400.01, 0) > GameRules.ENGINE_STOP);
        assertEquals(GameRules.ENGINE_ONE_THIRD,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 300, Math.toRadians(15)));
        assertEquals(GameRules.ENGINE_HALF,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 300, Math.PI));
    }

    @Test
    void approachingTargetsDoNotCausePrematureReverseWithinTheBand() {
        var sub = new Submarine("sub", "light", new Vector2(0, 0), 0, "bot");
        var target = new TorpedoBoat("approaching", "dark", new Vector2(0, 300), Math.PI, "bot");
        target.speed = 12;
        for (int distance = 250; distance <= 400; distance++)
            assertEquals(GameRules.ENGINE_STOP,
                    SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, distance, 0));
        assertEquals(GameRules.ENGINE_FULL_ASTERN,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 240, 0));
        target.heading = 0;
        assertEquals(GameRules.ENGINE_FULL,
                SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 450, 0),
                "Follow a departing target once it leaves the band");
    }

    @Test
    void stationaryTargetsAllowDifferentHoldingDistancesForTenMinutesWithoutForcedSurfacing() {
        for (double initialDistance : new double[]{240, 300, 350, 450}) {
            var sub = new Submarine("sub", "light", new Vector2(0, 0), 0, "bot");
            Vehicle target = enemy(initialDistance);
            WorldMap world = new WorldMap(99009, List.of());
            NavigationService navigation = new NavigationService();
            for (int tick = 0; tick < 6000; tick++) {
                SubmarineBotCalculation.updateDiveAndEscape(sub, List.of(target), tick * .1);
                double distance = sub.position().distanceTo(target.position());
                int order = SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, distance, 0);
                sub.applyCommand(order, 0);
                sub.update(.1, navigation, world);
                assertTrue(sub.isAtPeriscopeDepth(), "Holding must never trigger idle surfacing");
                assertTrue(distance > 220 && distance <= 450, "Remain in firing range: " + distance);
                if (distance >= 250 && distance <= 400) assertEquals(GameRules.ENGINE_STOP, order);
            }
            double finalDistance = sub.position().distanceTo(target.position());
            assertTrue(finalDistance >= 250 && finalDistance <= 400);
            if (initialDistance == 300 || initialDistance == 350)
                assertEquals(initialDistance, finalDistance, 1e-8, "No convergence on one common distance");
            assertEquals(0, sub.speed(), .001);
        }
    }
    @Test
    void sessionMaintainsShotDistanceInsteadOfDivingBesideStationaryTarget() {
        GameSession session = new GameSession(new GameSetup("submarine-range-test",
                new WorldMap(99009, List.of()), List.of(
                new FleetSetup("light", List.of(new VehicleSetup("sub", "light", new Vector2(0, 0),
                        0, "bot", 7, 0, 9999, "submarine", 0))),
                new FleetSetup("dark", List.of(new VehicleSetup("enemy", "dark", new Vector2(0, 400),
                        0, "player", 2, 0, 9999, "torpedo-boat", 0)))), List.of()));
        RadarService radar = new RadarService();
        NavigationService navigation = new NavigationService();
        for (int tick = 0; tick < 1200; tick++) {
            session.update(0.1, radar, navigation, session.worldMap());
            VehicleSnapshot sub = session.snapshot().ships().stream()
                    .filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
            double distance = Math.hypot(sub.x(), sub.z() - 400);
            assertTrue(distance > 220 && distance < 410, "Shot distance: " + distance);
            assertEquals("active", sub.state());
        }
    }

    @Test
    void friendlySubmarinesKeepDifferentAttackDistancesEvenWithoutHumanPlayers() {
        var world = new WorldMap(99009, List.of());
        var setup = new GameSetup("bot-only-holding-range", world, List.of(
                new FleetSetup("light", List.of(
                        new VehicleSetup("near-sub", "light", new Vector2(0, 100), 0, "bot", 2, 0, 9999, "submarine", -5.58),
                        new VehicleSetup("far-sub", "light", new Vector2(0, 50), 0, "bot", 2, 0, 9999, "submarine", -5.58))),
                new FleetSetup("dark", List.of(
                        new VehicleSetup("target", "dark", new Vector2(0, 400), 0, "bot", 2, 0, 9999, "torpedo-boat", 0)))), List.of());
        var calculation = new StandardBotCalculation(
                (boat, situation, tick, goal) -> situation.steer(boat, GameRules.ENGINE_STOP, 0),
                new SubmarineBotCalculation(), (plane, situation, tick, goal) -> {});
        var session = new GameSession(setup, 1, false, calculation, BotTargetSelection.standard());
        for (Vehicle vehicle : session.calculationShips()) if (vehicle instanceof Submarine sub) sub.botDepth("periscope");
        var radar = new RadarService();
        var navigation = new NavigationService();
        for (int tick = 0; tick < 1200; tick++) {
            session.update(.1, radar, navigation, world);
            for (Vehicle vehicle : session.calculationShips()) if (vehicle instanceof Submarine sub) {
                assertEquals("periscope", sub.depthState());
                assertEquals(GameRules.ENGINE_STOP, sub.engineOrder());
                assertEquals(sub.id().equals("near-sub") ? 100 : 50, sub.position().z(), 1e-8);
            }
        }
    }

    @Test
    void respawnPreparationDoesNotTurnSubmarineIntoSurfaceShip() throws Exception {
        GameSession session = new GameSession(new GameSetup("dense-land", new WorldMap(99009, List.of()),
                List.of(new FleetSetup("light", List.of(new VehicleSetup("sub", "light", new Vector2(0, 0),
                        Math.PI, "bot", 2, 0, 999, "submarine", 0)))), List.of()));
        var all = GameSession.class.getDeclaredMethod("allShips"); all.setAccessible(true);
        Vehicle ship = (Vehicle) ((List<?>) all.invoke(session)).get(0);
        ship.sink(1);
        var prepare = GameSession.class.getDeclaredMethod("prepareBotVehicleTypeForRespawn", Vehicle.class);
        prepare.setAccessible(true);
        prepare.invoke(session, ship);
        ship.respawn(new Vector2(0, 0), 0, 1);
        assertTrue(ship.isSubmarine());
        assertTrue(ship.isOnSurface());
    }

    @Test
    void specialMenuFleetResetsKeepThreeSubmarinesPerSide() {
        WorldMapService maps = new WorldMapService() {
            @Override WorldMap denseWorld() { return new WorldMap(99009, List.of()); }
            @Override WorldMap world() { return new WorldMap(99009, List.of()); }
        };
        GameStateService game = new GameStateService(new DefaultGameSetupFactory(maps),
                new RadarService(), new NavigationService(), TestGameConfigService.defaults());
        for (String setup : List.of("dense-land", "islands", "dense-land-crowded",
                "dense-land-crowded-reverse", "scout-plane")) {
            for (int repeat = 0; repeat < 2; repeat++) {
                GameSnapshot state = game.reset(new ResetGameRequest("bernd", setup));
                for (String team : List.of("light", "dark")) {
                    assertEquals(3, state.ships().stream().filter(s -> team.equals(s.teamId()))
                            .filter(s -> "submarine".equals(s.vehicleType())).count(), setup + ": " + team);
                }
            }
        }
    }

    @Test
    void newSessionHasNewInstanceEvenWithSameSetupId() {
        GameSetup setup = new GameSetup("same", new WorldMap(99009, List.of()), List.of(), List.of());
        GameSession first = new GameSession(setup);
        GameSession second = new GameSession(setup);
        assertEquals(first.snapshot().instanceId(), first.snapshot().instanceId());
        assertNotEquals(first.snapshot().instanceId(), second.snapshot().instanceId());
        assertEquals(first.snapshot().sessionId(), second.snapshot().sessionId());
    }

    @Test
    void defaultFleetHasThreeSubmarinesPerTeamWithoutAddingShips() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService() {
            @Override
            WorldMap denseWorld() {
                return new WorldMap(99009, List.of());
            }
        });
        GameSetup setup = factory.defaultSetup();
        assertEquals(2, setup.fleets().size());
        for (FleetSetup fleet : setup.fleets()) {
            assertEquals(15, fleet.ships().size());
            assertEquals(3, fleet.ships().stream().filter(s -> "submarine".equals(s.vehicleType())).count());
            assertEquals(11, fleet.ships().stream().filter(s -> "torpedo-boat".equals(s.vehicleType())).count());
            assertEquals(1, fleet.ships().stream().filter(s -> "scout-plane".equals(s.vehicleType())).count());
            assertEquals("torpedo-boat", fleet.ships().get(0).vehicleType());
        }
    }

    private Vehicle submarine() {
        Vehicle ship = new TorpedoBoat("sub", "light", new Vector2(0, 0), Math.PI, "bot");
        ship = ship.convertedTo("submarine");
        return ship;
    }

    private Vehicle enemy(double z) {
        return new TorpedoBoat("enemy", "dark", new Vector2(0, z), 0, "bot");
    }

    @Test
    void returnsToPeriscopeAtReducedSafeDistance() {
        Vehicle ship = submarine();
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(100)), 0);
        assertNotNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(219)), 1));
        assertNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(220)), 2));
        assertTrue(ship.isAtPeriscopeDepth());
    }

    @Test
    void alignedShotAllowsEarlierAscentButNearbyThreatStillPreventsIt() {
        Vehicle ship = submarine();
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(100)), 0);
        assertNotNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(200), enemy(150)), 1, true));
        assertTrue(ship.isFullySubmerged());
        assertNotNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(180)), 2, false));
        assertNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(180)), 3, true));
        assertTrue(ship.isAtPeriscopeDepth());
    }

    @Test
    void ascentShotRequiresTargetAheadAndNoFriendlyShipInLine() throws Exception {
        Vehicle sub = new TorpedoBoat("sub", "light", new Vector2(0, 0), 0, "bot");
        sub = sub.convertedTo("submarine");
        var method = GameSession.class.getDeclaredMethod("submarineHasAscentShot", Vehicle.class, Vehicle.class);
        method.setAccessible(true);
        GameSession empty = new GameSession(new GameSetup("test", new WorldMap(99009, List.of()), List.of(), List.of()));
        assertEquals(true, method.invoke(empty, sub, enemy(200)));
        assertEquals(false, method.invoke(empty, sub, enemy(-200)));
        GameSession blocked = new GameSession(new GameSetup("test", new WorldMap(99009, List.of()),
                List.of(new FleetSetup("light", List.of(new VehicleSetup("friend", "light", new Vector2(0, 90),
                        0, "bot", 2, 0, 99, "torpedo-boat", 0)))), List.of()));
        assertEquals(false, method.invoke(blocked, sub, enemy(200)));
    }

    @Test
    void divesAndUsesCurrentUnderwaterContactsWithHysteresis() {
        Vehicle ship = submarine();
        assertNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(400)), 0));
        assertTrue(ship.isAtPeriscopeDepth());
        assertEquals(new Vector2(0, 100), SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(100)), 1));
        assertTrue(ship.isFullySubmerged());
        assertEquals(new Vector2(0, 200), SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(200)), 2));
        assertTrue(ship.isFullySubmerged());
        assertNull(SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(300)), 3));
        assertTrue(ship.isAtPeriscopeDepth());
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(550)), 4);
        assertTrue(ship.isAtPeriscopeDepth());
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(700)), 5);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void ignoresFriendsPlanesAndContactsOutsideUnderwaterRange() {
        Vehicle ship = submarine();
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(enemy(100)), 0);
        Vehicle friend = new TorpedoBoat("friend", "light", new Vector2(0, 10), 0, "bot");
        Vehicle plane = enemy(20);
        plane = plane.convertedTo("scout-plane");
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(friend, plane, enemy(GameRules.BOT_SUBMARINE_UNDERWATER_RANGE + 1)), 1);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void lackOfMovementNeverForcesSurfacingAtEitherDivingDepth() {
        for (double enemyDistance : new double[]{100, 300}) {
            var ship = (Submarine) submarine();
            for (int second = 0; second <= 120; second++) {
                SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(enemyDistance)), second);
                assertFalse(ship.isOnSurface(), "Blocked or holding boats stay submerged despite elapsed time");
                assertEquals(enemyDistance == 100 ? "submerged" : "periscope", ship.depthState());
            }
        }
    }

    @Test
    void aBlockedBoatStillReassessesThreatsInsteadOfWaitingForASurfaceRecoveryTimeout() {
        var ship = (Submarine) submarine();
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(100)), 0);
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(100)), 30);
        assertTrue(ship.isFullySubmerged());
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(300)), 31);
        assertTrue(ship.isAtPeriscopeDepth());
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(100)), 32);
        assertTrue(ship.isFullySubmerged(), "A close threat still has priority over holding position");
    }

    @Test
    void lossOfEnemyContactCanStillLeadToNormalSurfacing() {
        var ship = (Submarine) submarine();
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(100)), 0);
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(enemy(100)), 30);
        assertTrue(ship.isFullySubmerged());
        SubmarineBotCalculation.updateDiveAndEscape(ship, List.of(), 31);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void activeRetreatDoesNotSurfaceBesideEnemyAfterNinetySeconds() {
        Vehicle ship = submarine();
        WorldMap world = new WorldMap(99009, List.of());
        NavigationService navigation = new NavigationService();
        for (int t = 0; t <= 120; t++) {
            Vehicle contact = new TorpedoBoat("enemy", "dark", ship.position().add(new Vector2(0, 100)), 0, "bot");
            SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(contact), t);
            ship.applyCommand(7, 0);
            ship.update(1, navigation, world);
        }
        assertTrue(ship.isFullySubmerged());
        assertTrue(ship.canFire(120), "Settled deep boats can now fire depth-running torpedoes");
        Vehicle distant = new TorpedoBoat("enemy", "dark", ship.position().add(new Vector2(0, 300)), 0, "bot");
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(distant), 121);
        assertTrue(ship.isAtPeriscopeDepth());
        SubmarineBotCalculation.updateDiveAndEscape((Submarine) ship, List.of(), 122);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void depthMovesGraduallyAndDeepBotCannotFireDuringAscent() {
        Vehicle ship = submarine();
        WorldMap world = new WorldMap(99009, List.of());
        NavigationService navigation = new NavigationService();
        ship.botDepth("submerged");
        ship.update(1, navigation, world);
        assertEquals(-0.84, ship.y(), 0.0001);
        assertFalse(ship.canFire(100));
        for (int i = 0; i < 25; i++) ship.update(1, navigation, world);
        assertEquals(-19.26, ship.y(), 0.0001);
        ship.botDepth("periscope");
        assertFalse(ship.canFire(100));
        for (int i = 0; i < 20; i++) ship.update(1, navigation, world);
        assertEquals(-5.58, ship.y(), 0.0001);
        assertTrue(ship.canFire(100));
    }

    @Test
    void playerDepthIsNotControlledByBot() {
        Vehicle ship = submarine();
        ship.controlledBy("player");
        ship.botDepth("submerged");
        assertTrue(ship.isOnSurface());
    }

    @Test
    void sessionRetreatsAndKeepsFiringDisabledWhileDeep() {
        GameSession session = new GameSession(new GameSetup("submarine-test",
                new WorldMap(99009, List.of()), List.of(
                new FleetSetup("light", List.of(new VehicleSetup("sub", "light", new Vector2(0, 0),
                        Math.PI, "bot", 7, 0, 0, "submarine", 0))),
                new FleetSetup("dark", List.of(new VehicleSetup("enemy", "dark", new Vector2(0, 100),
                        0, "player", 2, 0, 999, "torpedo-boat", 0)))), List.of(new Vector2(0, 0))));
        for (int i = 0; i < 100; i++) {
            session.update(0.1, new RadarService(), new NavigationService(), session.worldMap());
        }
        VehicleSnapshot sub = session.snapshot().ships().stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
        assertTrue(sub.z() < -20, "Bot must actively increase separation");
        assertTrue(session.snapshot().torpedoes().isEmpty());
        assertTrue(sub.y() < 0);
    }
}
