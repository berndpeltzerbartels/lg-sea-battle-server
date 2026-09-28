package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubmarineBotTest {
    @Test
    void periscopeAttackSlowsDownAndBacksAwayInsteadOfCharging() {
        Ship sub = submarine();
        Ship target = enemy(300);
        assertEquals(4, sub.submarineBot().attackEngineOrder(sub, target, 300, 0));
        assertEquals(1, sub.submarineBot().attackEngineOrder(sub, target, 240, 0));
        assertEquals(1, sub.submarineBot().attackEngineOrder(sub, target, 300, 0));
        assertEquals(7, sub.submarineBot().attackEngineOrder(sub, target, 650, 0));
        assertEquals(5, sub.submarineBot().attackEngineOrder(sub, target, 300, Math.PI));
    }

    @Test
    void periscopeAttackMatchesMovingTargetAndNeverOrdersStop() {
        Ship sub = new Ship("sub", "light", new Vector2(0, 0), 0, "bot");
        sub.vehicleType("submarine");
        Ship target = enemy(300);
        target.applyCommand(7, 0);
        target.update(20, new NavigationService(), new WorldMap(99009, List.of()));
        assertEquals(7, sub.submarineBot().attackEngineOrder(sub, target, 300, 0));
        Ship approaching = new Ship("approaching", "dark", new Vector2(0, 300), Math.PI, "bot");
        approaching.applyCommand(7, 0);
        approaching.update(20, new NavigationService(), new WorldMap(99009, List.of()));
        assertEquals(0, sub.submarineBot().attackEngineOrder(sub, approaching, 300, 0));
        for (int distance = 130; distance <= 650; distance++) {
            assertNotEquals(2, sub.submarineBot().attackEngineOrder(sub, target, distance, 0));
        }
    }

    @Test
    void distanceControlKeepsMovingWithinFiringRangeForTenMinutes() {
        Ship sub = new Ship("sub", "light", new Vector2(0, 0), 0, "bot");
        sub.vehicleType("submarine");
        Ship target = enemy(400);
        WorldMap world = new WorldMap(99009, List.of());
        NavigationService navigation = new NavigationService();
        boolean movedForward = false;
        boolean movedBackward = false;
        for (int tick = 0; tick < 6000; tick++) {
            sub.submarineBot().update(sub, List.of(target), tick * 0.1);
            double distance = sub.position().distanceTo(target.position());
            sub.applyCommand(sub.submarineBot().attackEngineOrder(sub, target, distance, 0), 0);
            sub.update(0.1, navigation, world);
            assertTrue(sub.isAtPeriscopeDepth(), "Must not trigger idle surfacing or emergency diving");
            assertTrue(distance > 220 && distance <= 400, "Must stay in firing range: " + distance);
            movedForward |= sub.speed() > 0.5;
            movedBackward |= sub.speed() < -0.5;
        }
        assertTrue(movedForward && movedBackward);
    }
    @Test
    void sessionMaintainsShotDistanceInsteadOfDivingBesideStationaryTarget() {
        GameSession session = new GameSession(new GameSetup("submarine-range-test",
                new WorldMap(99009, List.of()), List.of(
                new FleetSetup("light", List.of(new ShipSetup("sub", "light", new Vector2(0, 0),
                        0, "bot", 7, 0, 9999, "submarine", 0))),
                new FleetSetup("dark", List.of(new ShipSetup("enemy", "dark", new Vector2(0, 400),
                        0, "player", 2, 0, 9999, "torpedo-boat", 0)))), List.of()));
        RadarService radar = new RadarService();
        NavigationService navigation = new NavigationService();
        for (int tick = 0; tick < 1200; tick++) {
            session.update(0.1, radar, navigation, session.worldMap());
            ShipSnapshot sub = session.snapshot().ships().stream()
                    .filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
            double distance = Math.hypot(sub.x(), sub.z() - 400);
            assertTrue(distance > 220 && distance < 410, "Shot distance: " + distance);
            assertEquals("active", sub.state());
        }
    }

    @Test
    void respawnPreparationDoesNotTurnSubmarineIntoSurfaceShip() throws Exception {
        Ship ship = submarine();
        ship.sink(1);
        GameSession session = new GameSession(new GameSetup("dense-land",
                new WorldMap(99009, List.of()), List.of(), List.of()));
        var prepare = GameSession.class.getDeclaredMethod("prepareBotVehicleTypeForRespawn", Ship.class);
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
                new RadarService(), new NavigationService());
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

    private Ship submarine() {
        Ship ship = new Ship("sub", "light", new Vector2(0, 0), Math.PI, "bot");
        ship.vehicleType("submarine");
        return ship;
    }

    private Ship enemy(double z) {
        return new Ship("enemy", "dark", new Vector2(0, z), 0, "bot");
    }

    @Test
    void returnsToPeriscopeAtReducedSafeDistance() {
        Ship ship = submarine();
        ship.submarineBot().update(ship, List.of(enemy(100)), 0);
        assertNotNull(ship.submarineBot().update(ship, List.of(enemy(219)), 1));
        assertNull(ship.submarineBot().update(ship, List.of(enemy(220)), 2));
        assertTrue(ship.isAtPeriscopeDepth());
    }

    @Test
    void alignedShotAllowsEarlierAscentButNearbyThreatStillPreventsIt() {
        Ship ship = submarine();
        ship.submarineBot().update(ship, List.of(enemy(100)), 0);
        assertNotNull(ship.submarineBot().update(ship, List.of(enemy(200), enemy(150)), 1, true));
        assertTrue(ship.isFullySubmerged());
        assertNotNull(ship.submarineBot().update(ship, List.of(enemy(180)), 2, false));
        assertNull(ship.submarineBot().update(ship, List.of(enemy(180)), 3, true));
        assertTrue(ship.isAtPeriscopeDepth());
    }

    @Test
    void ascentShotRequiresTargetAheadAndNoFriendlyShipInLine() throws Exception {
        Ship sub = new Ship("sub", "light", new Vector2(0, 0), 0, "bot");
        sub.vehicleType("submarine");
        var method = GameSession.class.getDeclaredMethod("submarineHasAscentShot", Ship.class, Ship.class);
        method.setAccessible(true);
        GameSession empty = new GameSession(new GameSetup("test", new WorldMap(99009, List.of()), List.of(), List.of()));
        assertEquals(true, method.invoke(empty, sub, enemy(200)));
        assertEquals(false, method.invoke(empty, sub, enemy(-200)));
        GameSession blocked = new GameSession(new GameSetup("test", new WorldMap(99009, List.of()),
                List.of(new FleetSetup("light", List.of(new ShipSetup("friend", "light", new Vector2(0, 90),
                        0, "bot", 2, 0, 99, "torpedo-boat", 0)))), List.of()));
        assertEquals(false, method.invoke(blocked, sub, enemy(200)));
    }

    @Test
    void divesAndUsesCurrentUnderwaterContactsWithHysteresis() {
        Ship ship = submarine();
        SubmarineBot bot = ship.submarineBot();
        assertNull(bot.update(ship, List.of(enemy(400)), 0));
        assertTrue(ship.isAtPeriscopeDepth());
        assertEquals(new Vector2(0, 100), bot.update(ship, List.of(enemy(100)), 1));
        assertTrue(ship.isFullySubmerged());
        assertEquals(new Vector2(0, 200), bot.update(ship, List.of(enemy(200)), 2));
        assertTrue(ship.isFullySubmerged());
        assertNull(bot.update(ship, List.of(enemy(300)), 3));
        assertTrue(ship.isAtPeriscopeDepth());
        bot.update(ship, List.of(enemy(550)), 4);
        assertTrue(ship.isAtPeriscopeDepth());
        bot.update(ship, List.of(enemy(700)), 5);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void ignoresFriendsPlanesAndContactsOutsideUnderwaterRange() {
        Ship ship = submarine();
        ship.submarineBot().update(ship, List.of(enemy(100)), 0);
        Ship friend = new Ship("friend", "light", new Vector2(0, 10), 0, "bot");
        Ship plane = enemy(20);
        plane.vehicleType("scout-plane");
        ship.submarineBot().update(ship, List.of(friend, plane, enemy(SubmarineBot.UNDERWATER_RANGE + 1)), 1);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void stuckBotSurfacesAndDoesNotImmediatelyDiveAgain() {
        Ship ship = submarine();
        ship.submarineBot().update(ship, List.of(enemy(100)), 0);
        ship.submarineBot().update(ship, List.of(enemy(100)), 15);
        assertTrue(ship.isOnSurface());
        ship.submarineBot().update(ship, List.of(enemy(100)), 44);
        assertTrue(ship.isOnSurface());
        ship.submarineBot().update(ship, List.of(enemy(100)), 46);
        assertTrue(ship.isFullySubmerged());
    }

    @Test
    void activeRetreatDoesNotSurfaceBesideEnemyAfterNinetySeconds() {
        Ship ship = submarine();
        WorldMap world = new WorldMap(99009, List.of());
        NavigationService navigation = new NavigationService();
        for (int t = 0; t <= 120; t++) {
            Ship contact = new Ship("enemy", "dark", ship.position().add(new Vector2(0, 100)), 0, "bot");
            ship.submarineBot().update(ship, List.of(contact), t);
            ship.applyCommand(7, 0);
            ship.update(1, navigation, world);
        }
        assertTrue(ship.isFullySubmerged());
        assertFalse(ship.canFire(120));
        Ship distant = new Ship("enemy", "dark", ship.position().add(new Vector2(0, 300)), 0, "bot");
        ship.submarineBot().update(ship, List.of(distant), 121);
        assertTrue(ship.isAtPeriscopeDepth());
        ship.submarineBot().update(ship, List.of(), 122);
        assertTrue(ship.isOnSurface());
    }

    @Test
    void depthMovesGraduallyAndDeepBotCannotFireDuringAscent() {
        Ship ship = submarine();
        WorldMap world = new WorldMap(99009, List.of());
        NavigationService navigation = new NavigationService();
        ship.botDepth("submerged");
        ship.update(1, navigation, world);
        assertEquals(-0.84, ship.y(), 0.0001);
        assertFalse(ship.canFire(100));
        for (int i = 0; i < 15; i++) ship.update(1, navigation, world);
        assertEquals(-9.63, ship.y(), 0.0001);
        ship.botDepth("periscope");
        assertFalse(ship.canFire(100));
        for (int i = 0; i < 10; i++) ship.update(1, navigation, world);
        assertEquals(-5.58, ship.y(), 0.0001);
        assertTrue(ship.canFire(100));
    }

    @Test
    void playerDepthIsNotControlledByBot() {
        Ship ship = submarine();
        ship.controlledBy("player");
        ship.botDepth("submerged");
        assertTrue(ship.isOnSurface());
    }

    @Test
    void sessionRetreatsAndKeepsFiringDisabledWhileDeep() {
        GameSession session = new GameSession(new GameSetup("submarine-test",
                new WorldMap(99009, List.of()), List.of(
                new FleetSetup("light", List.of(new ShipSetup("sub", "light", new Vector2(0, 0),
                        Math.PI, "bot", 7, 0, 0, "submarine", 0))),
                new FleetSetup("dark", List.of(new ShipSetup("enemy", "dark", new Vector2(0, 100),
                        0, "player", 2, 0, 999, "torpedo-boat", 0)))), List.of(new Vector2(0, 0))));
        for (int i = 0; i < 100; i++) {
            session.update(0.1, new RadarService(), new NavigationService(), session.worldMap());
        }
        ShipSnapshot sub = session.snapshot().ships().stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
        assertTrue(sub.z() < -20, "Bot must actively increase separation");
        assertTrue(session.snapshot().torpedoes().isEmpty());
        assertTrue(sub.y() < 0);
    }
}
