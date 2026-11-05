package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class HumanRespawnSafetyTest {
    private final NavigationService navigation = new NavigationService();
    private final RadarService radar = new RadarService();
    private final WorldMap water = new WorldMap(1, List.of());

    private VehicleSetup ship(String id, String team, String controller, double x) {
        return new VehicleSetup(id, team, new Vector2(x, 0), 0, controller, ENGINE_STOP, 0, 9999, "torpedo-boat", 0);
    }

    private GameSession session(List<Vector2> points, List<FleetSetup> fleets) {
        return new GameSession(new GameSetup("human-respawn", water, fleets, points));
    }

    private Vehicle player() {
        return new Submarine("player", "light", new Vector2(0, -1000), 0, "captain");
    }

    @Test void avoidsEnemyBotsAndHumansButAllowsNearbyFriends() {
        for (String enemyController : List.of("bot", "enemy-captain")) {
            var s = session(List.of(new Vector2(400, 0), new Vector2(1000, 0)), List.of(
                    new FleetSetup("dark", List.of(ship("enemy", "dark", enemyController, 0))),
                    new FleetSetup("light", List.of(ship("friend", "light", "friend-captain", 800)))));
            assertEquals(new Vector2(1000, 0), s.findRespawnPosition(player(), navigation, water, radar));
        }
    }

    @Test void variesAmongSafePointsInsteadOfAlwaysChoosingTheMostRemoteOne() {
        List<Vector2> points = List.of(new Vector2(1000, 0), new Vector2(1600, 0), new Vector2(2400, 0));
        var s = session(points, List.of(new FleetSetup("dark", List.of(ship("enemy", "dark", "bot", 0)))));
        var seen = new HashSet<Vector2>();
        Vector2 previous = null;
        for (int i = 0; i < 30; i++) {
            Vector2 chosen = s.findRespawnPosition(player(), navigation, water, radar);
            assertNotEquals(previous, chosen, "Avoid immediately repeating the previous point");
            seen.add(chosen);
            previous = chosen;
        }
        assertEquals(new HashSet<>(points), seen, "Also use nearby safe points rather than only the farthest");
    }

    @Test void repeatsTheOnlySafePointRatherThanPreferringANewDangerousPoint() {
        var s = session(List.of(new Vector2(400, 0), new Vector2(1000, 0)), List.of(
                new FleetSetup("dark", List.of(ship("enemy", "dark", "bot", 0)))));
        for (int i = 0; i < 3; i++)
            assertEquals(new Vector2(1000, 0), s.findRespawnPosition(player(), navigation, water, radar));
    }

    @Test void noSafePointUsesTheBestAvailableClearanceWithoutWaiting() {
        var s = session(List.of(new Vector2(300, 0), new Vector2(700, 0)), List.of(
                new FleetSetup("dark", List.of(ship("enemy", "dark", "bot", 0)))));
        assertEquals(new Vector2(700, 0), s.findRespawnPosition(player(), navigation, water, radar));
    }

    @Test void safePointsStillRespectFriendlyHullSpacingAndLand() {
        var land = new Landmass("island", "blocked", 1600, 0, 30, 30, 30, 30, 36, 36,
                null, 1, null, null, null, List.of(), List.of(), List.of());
        var world = new WorldMap(2, List.of(land));
        var s = new GameSession(new GameSetup("human-spawn-physical", world, List.of(
                new FleetSetup("light", List.of(ship("friend", "light", "bot", 1000)))),
                List.of(new Vector2(1000, 0), new Vector2(1600, 0), new Vector2(2400, 0))));
        for (int i = 0; i < 10; i++)
            assertEquals(new Vector2(2400, 0), s.findRespawnPosition(player(), navigation, world, radar));
    }

    @Test void realHumanRespawnUsesTheNewClearanceAfterDeath() throws Exception {
      for (boolean explicitAssignment : List.of(false, true)) {
        var s = session(List.of(new Vector2(400, 0), new Vector2(1000, 0)), List.of(
                new FleetSetup("light", List.of(ship("player", "light", "captain", -1200))),
                new FleetSetup("dark", List.of(ship("enemy", "dark", "bot", 0)))));
        Vehicle human = s.calculationShips().stream().filter(v -> v.id().equals("player")).findFirst().orElseThrow();
        Method sink = GameSession.class.getDeclaredMethod("sinkShip", Vehicle.class);
        sink.setAccessible(true);
        sink.invoke(s, human);
        s.updateIdle(RESPAWN_DELAY_SECONDS + .1, radar, navigation, water);
        if (explicitAssignment) s.assignPlayerVehicle("captain", "light", "torpedo-boat");
        else s.applyPlayerState(new PlayerStateUpdate("captain", "light", 400, 0, 0, 0, 0,
                ENGINE_STOP, 0, 0, false, "torpedo-boat"), navigation, water);
        Vehicle replacement = s.calculationShips().stream()
                .filter(v -> "captain".equals(v.controlledBy())).findFirst().orElseThrow();
        assertEquals("active", replacement.state());
        assertEquals(new Vector2(1000, 0), replacement.position());
        assertEquals("captain", replacement.controlledBy());
        assertEquals(2, s.calculationShips().size(), "Reuse a fleet slot rather than adding another ship");
      }
    }
}
