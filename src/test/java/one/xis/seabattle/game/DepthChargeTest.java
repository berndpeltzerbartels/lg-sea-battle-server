package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DepthChargeTest {
    private ShipSetup ship(String id, String team, double x, double z, String type, double y) {
        return new ShipSetup(id, team, new Vector2(x, z), 0, "player-" + id, 2, 0, 999, type, y);
    }
    private GameSession session() {
        return new GameSession(new GameSetup("charges", new WorldMap(42, List.of()), List.of(
                new FleetSetup("light", List.of(ship("boat", "light", 0, 0, "torpedo-boat", 0))),
                new FleetSetup("dark", List.of(
                        ship("deep", "dark", 0, -13, "submarine", -20),
                        ship("edge", "dark", 10, -13, "submarine", -2),
                        ship("outside", "dark", 30, -13, "submarine", -20),
                        ship("surface", "dark", 0, -13, "torpedo-boat", 0)))), List.of(new Vector2(1000, 1000))));
    }
    private void tick(GameSession s, double dt) {
        s.updateIdle(dt, new RadarService(), new NavigationService(), s.worldMap());
    }
    @Test void pairIsStaggeredAndExplodesOnceAcrossDepthsWithoutHurtingSurfaceShips() {
        var s = session();
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        assertEquals(1, s.snapshot().depthCharges().size());
        assertThrows(IllegalArgumentException.class, () -> s.dropDepthCharges("player-boat", "player-boat", "boat"));
        tick(s, 1.2);
        assertEquals(2, s.snapshot().depthCharges().size());
        assertEquals(1.2, s.snapshot().depthCharges().get(1).releasedAt());
        tick(s, 1.3);
        var hit = s.snapshot().depthCharges().get(0);
        assertTrue(hit.exploded());
        assertTrue(hit.targetShipIds().containsAll(List.of("deep", "edge")));
        assertFalse(hit.targetShipIds().contains("outside"));
        assertFalse(hit.targetShipIds().contains("surface"));
        assertEquals(2, s.snapshot().killsByPlayer().get("player-boat"));
        tick(s, 1.2);
        assertTrue(s.snapshot().depthCharges().get(1).exploded());
        assertTrue(s.snapshot().depthCharges().get(1).targetShipIds().isEmpty());
        tick(s, 5);
        assertTrue(s.snapshot().depthCharges().isEmpty());
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        assertEquals(1, s.snapshot().depthCharges().size());
    }
    @Test void otherPlayersAndSubmarinesCannotDrop() {
        var s = session();
        assertThrows(IllegalArgumentException.class, () -> s.dropDepthCharges("other", "other", "boat"));
        assertThrows(IllegalArgumentException.class, () -> s.dropDepthCharges("player-deep", "player-deep", "deep"));
        assertTrue(s.snapshot().depthCharges().isEmpty());
    }
}
