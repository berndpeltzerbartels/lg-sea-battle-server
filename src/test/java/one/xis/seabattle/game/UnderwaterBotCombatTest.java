package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UnderwaterBotCombatTest {
    private static final double DEEP = -3.21 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private final WorldMap map = new WorldMap(99012, List.of());
    private final NavigationService navigation = new NavigationService();

    @Test
    void botDetectsDivesAndFiresAtDeepEnemyWithinSonarRange() {
        GameSession session = sessionWithEnemy(300);
        boolean fired = false;
        boolean hit = false;
        for (int tick = 0; tick < 1200 && !hit; tick++) {
            session.update(.1, new RadarService(), navigation, map);
            for (var torpedo : session.snapshot().torpedoes()) {
                fired = true;
                assertEquals(DEEP + .2 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE, torpedo.y(), .11);
            }
            hit = session.snapshot().torpedoImpacts().stream()
                    .anyMatch(impact -> "enemy".equals(impact.targetShipId()));
        }
        assertTrue(fired, "Must launch a deep torpedo, not just detect the contact");
        assertTrue(hit, "Existing aiming logic must actually hit the submerged hull");
    }

    @Test
    void distantDeepEnemyIsNotDetectedBySurfaceRadar() {
        GameSession session = sessionWithEnemy(SubmarineBot.UNDERWATER_RANGE + 100);
        session.update(.1, new RadarService(), navigation, map);
        assertEquals("surface", session.snapshot().ships().stream()
                .filter(ship -> ship.id().equals("sub")).findFirst().orElseThrow().depthState());
        assertTrue(session.snapshot().torpedoes().isEmpty());
    }

    @Test
    void deepEnemyIsAThreatButFriendDoesNotCauseDive() {
        Ship sub = submarine("sub", "light", 0, false);
        Ship enemy = submarine("enemy", "dark", 100, true);
        assertEquals(enemy.position(), sub.submarineBot().update(sub, List.of(enemy), 0));
        assertTrue(sub.isFullySubmerged());
        Ship other = submarine("other", "light", 0, false);
        Ship friend = submarine("friend", "light", 100, true);
        other.submarineBot().update(other, List.of(friend), 0);
        assertTrue(other.isOnSurface());
    }

    @Test
    void friendlySubmarineBlocksDeepShotButSurfaceShipDoesNot() throws Exception {
        for (double friendY : new double[]{DEEP, 0}) {
            GameSession session = new GameSession(new GameSetup("friendly-line", map, List.of(
                    new FleetSetup("light", List.of(new ShipSetup("friend", "light", new Vector2(0, 100),
                            0, "friend-player", 2, 0, 999, "submarine", friendY)))), List.of()));
            session.updatePlayerState(new PlayerStateUpdate("friend-player", "light", 0, 100, 0, 0, 0, 2, 0, 0,
                    false, "submarine", friendY, 0, null, null, null, null,
                    friendY == 0 ? "surface" : "submerged"), navigation, map);
            var method = GameSession.class.getDeclaredMethod("torpedoLaunchWouldHitFriendlyShip",
                    Ship.class, Vector2.class, double.class, double.class);
            method.setAccessible(true);
            assertEquals(friendY == DEEP, method.invoke(session, submarine("sub", "light", 0, true),
                    new Vector2(0, 15), 0.0, DEEP + .2 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE));
        }
    }

    @Test
    void friendlyDeepHumanAndBotTriggerExistingCollisionAvoidance() throws Exception {
        GameSession session = new GameSession(new GameSetup("avoid", map, List.of(), List.of()));
        Ship sub = submarine("z-sub", "light", 0, true);
        Ship friend = submarine("a-friend", "light", 25, true);
        var botAvoid = GameSession.class.getDeclaredMethod("avoidFriendlyBotDeadlock", Ship.class, List.class);
        botAvoid.setAccessible(true);
        assertEquals(true, botAvoid.invoke(session, sub, List.of(friend)));
        friend.controlledBy("player");
        var humanAvoid = GameSession.class.getDeclaredMethod("avoidShipAhead", Ship.class, List.class,
                NavigationService.class, WorldMap.class);
        humanAvoid.setAccessible(true);
        assertEquals(true, humanAvoid.invoke(session, sub, List.of(friend), navigation, map));
        Ship surfaceFriend = new Ship("surface", "light", new Vector2(0, 25), 0, "player");
        assertEquals(false, humanAvoid.invoke(session, sub, List.of(surfaceFriend), navigation, map));
    }

    private Ship submarine(String id, String team, double z, boolean deep) {
        Ship ship = new Ship(id, team, new Vector2(0, z), 0, "bot");
        ship.vehicleType("submarine");
        if (deep) {
            ship.botDepth("submerged");
            ship.applyCommand(2, 0);
            ship.update(20, navigation, map);
        }
        return ship;
    }

    private GameSession sessionWithEnemy(double distance) {
        GameSession session = new GameSession(new GameSetup("deep-combat", map, List.of(
                new FleetSetup("light", List.of(new ShipSetup("sub", "light", new Vector2(0, 0),
                        0, "bot", 4, 0, 0, "submarine", 0))),
                new FleetSetup("dark", List.of(new ShipSetup("enemy", "dark", new Vector2(0, distance),
                        0, "player", 2, 0, 999, "submarine", DEEP)))), List.of()));
        session.updatePlayerState(new PlayerStateUpdate("player", "dark", 0, distance, 0, 0, 0, 2, 0, 0,
                false, "submarine", DEEP, 0, null, null, null, null, "submerged"), navigation, map);
        return session;
    }
}
