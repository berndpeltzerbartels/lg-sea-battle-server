package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BotDepthChargeTest {
    private final RadarService radar = new RadarService();
    private final NavigationService navigation = new NavigationService();

    private GameSession session(String controller, String boatType, String targetTeam, String depth, double distance) {
        var boat = new ShipSetup("boat", "light", new Vector2(0, 0), 0, controller, 2, 0, 999, boatType, 0);
        var target = new ShipSetup("sub", targetTeam, new Vector2(distance, 0), 0, "diver", 2, 0, 999, "submarine", 0);
        var fleets = targetTeam.equals("light") ? List.of(new FleetSetup("light", List.of(boat, target)))
                : List.of(new FleetSetup("light", List.of(boat)), new FleetSetup(targetTeam, List.of(target)));
        var session = new GameSession(new GameSetup("bot-charges", new WorldMap(42, List.of()), fleets,
                List.of(new Vector2(1000, 1000))));
        position(session, targetTeam, depth, distance);
        return session;
    }

    private void position(GameSession s, String team, String depth, double distance) {
        double y = depth.equals("surface") ? 0 : depth.equals("periscope") ? -5.58 : SeaBattleGameConfig.SUBMARINE_DEEP_Y;
        s.updatePlayerState(new PlayerStateUpdate("diver", team, distance, 0, 0, 0, 0, 2, 0, 0,
                false, "submarine", y, 0, null, null, null, null, depth), navigation, s.worldMap());
    }

    private void tick(GameSession s, double dt) { s.update(dt, radar, navigation, s.worldMap()); }

    @Test void dropsNormalSalvoOnNearbyEnemyAtEitherSubmergedDepth() {
        for (String depth : List.of("periscope", "submerged")) {
            var s = session("bot", "torpedo-boat", "dark", depth, 8);
            tick(s, 0);
            assertEquals(1, s.snapshot().depthCharges().size());
            assertEquals("boat", s.snapshot().depthCharges().get(0).shipId());
            assertEquals(24, s.snapshot().depthCharges().get(0).radius());
            s.updateIdle(2.5, radar, navigation, s.worldMap());
            assertEquals(List.of("sub"), s.snapshot().depthCharges().get(0).targetShipIds());
            s.updateIdle(.5, radar, navigation, s.worldMap());
            assertEquals(3, s.snapshot().depthCharges().size());
        }
    }

    @Test void ignoresFriendlySurfacedAndDistantSubmarinesAndNonBotLaunchers() {
        for (var s : List.of(
                session("bot", "torpedo-boat", "light", "submerged", 8),
                session("bot", "torpedo-boat", "dark", "surface", 8),
                session("bot", "torpedo-boat", "dark", "submerged", 13),
                session("captain", "torpedo-boat", "dark", "submerged", 8),
                session("bot", "submarine", "dark", "submerged", 8),
                session("bot", "scout-plane", "dark", "submerged", 8))) {
            tick(s, 0);
            assertTrue(s.snapshot().depthCharges().isEmpty());
        }
    }

    @Test void checksTwicePerSecondAndDoesNotQueueRepeatedSalvos() {
        var s = session("bot", "torpedo-boat", "dark", "submerged", 50);
        tick(s, 0);
        position(s, "dark", "submerged", 8);
        tick(s, .1);
        assertTrue(s.snapshot().depthCharges().isEmpty());
        tick(s, .4);
        assertEquals(1, s.snapshot().depthCharges().size());
        for (int i = 0; i < 10; i++) tick(s, .1);
        assertEquals(1, s.snapshot().depthCharges().size());
        assertFalse(s.snapshot().depthChargeControls().get("boat").queued());
    }
}
