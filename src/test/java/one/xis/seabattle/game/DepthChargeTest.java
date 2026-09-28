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
                        ship("edge", "dark", 22, -13, "submarine", -2),
                        ship("outside", "dark", 30, -13, "submarine", -20),
                        ship("surface", "dark", 0, -13, "torpedo-boat", 0)))), List.of(new Vector2(1000, 1000))));
    }
    private void tick(GameSession s, double dt) {
        s.updateIdle(dt, new RadarService(), new NavigationService(), s.worldMap());
    }
    @Test void sternThenPairedSidesThenSternExplodeOnceAcrossDepthsWithoutHurtingSurfaceShips() {
        var s = session();
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        assertEquals(1, s.snapshot().depthCharges().size());
        tick(s, 2.5);
        assertEquals(1, s.snapshot().depthCharges().size());
        var hit = s.snapshot().depthCharges().get(0);
        assertEquals(24, hit.radius());
        assertEquals(4.8, hit.readyAt());
        assertTrue(hit.exploded());
        assertTrue(hit.targetShipIds().containsAll(List.of("deep", "edge")));
        assertFalse(hit.targetShipIds().contains("outside"));
        assertFalse(hit.targetShipIds().contains("surface"));
        assertEquals(2, s.snapshot().killsByPlayer().get("player-boat"));
        tick(s, .5);
        assertEquals(3, s.snapshot().depthCharges().size());
        var port = s.snapshot().depthCharges().get(1);
        var starboard = s.snapshot().depthCharges().get(2);
        assertEquals(2, port.lane());
        assertEquals(3, starboard.lane());
        assertEquals(3, port.releasedAt());
        assertEquals(port.releasedAt(), starboard.releasedAt());
        assertEquals(-24, port.x(), .001);
        assertEquals(24, starboard.x(), .001);
        assertEquals(port.radius() + starboard.radius(),
                new Vector2(port.x(), port.z()).distanceTo(new Vector2(starboard.x(), starboard.z())), .001);
        tick(s, 6);
        var fourth = s.snapshot().depthCharges().stream().filter(c -> c.lane() == 1).findFirst().orElseThrow();
        assertEquals(9, fourth.releasedAt());
        assertEquals(13.8, fourth.readyAt(), .001);
        tick(s, 2.5);
        assertTrue(s.snapshot().depthCharges().stream().filter(c -> c.id().equals(fourth.id())).findFirst().orElseThrow().exploded());
        tick(s, 3.6);
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

    @Test void oneQueuedSalvoStartsAfterLastReleaseAndHonorsEveryLaunchersReload() {
        var s = session();
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        s.dropDepthCharges("player-boat", "lookout", "boat");
        assertTrue(s.snapshot().depthChargeControls().get("boat").queued());
        assertThrows(IllegalArgumentException.class, () -> s.dropDepthCharges("player-boat", "third", "boat"));
        var releases = new java.util.LinkedHashMap<String, DepthChargeSnapshot>();
        for (int frame = 0; frame < 400; frame++) {
            s.snapshot().depthCharges().forEach(c -> releases.putIfAbsent(c.id(), c));
            tick(s, .05);
        }
        assertEquals(8, releases.size());
        var shots = List.copyOf(releases.values());
        assertEquals("lookout", shots.get(4).playerId());
        assertTrue(shots.get(4).releasedAt() >= shots.get(3).releasedAt());
        assertTrue(shots.get(4).releasedAt() - shots.get(3).releasedAt() < .11,
                "no arbitrary delay between salvos once the first rack is ready");
        var lastByLane = new java.util.HashMap<Integer, Double>();
        for (var shot : shots) {
            var previous = lastByLane.put(shot.lane(), shot.releasedAt());
            if (previous != null) assertTrue(shot.releasedAt() - previous >= 4.8 - 1e-8);
        }
        assertFalse(s.snapshot().depthChargeControls().get("boat").queued());
    }

    @Test void fastSalvosWaitOnlyForTheFirstRacksReloadAndThenPostponeAnyUnreadyLane() {
        var s = session();
        s.applyPlayerState(new PlayerStateUpdate("player-boat", "light", 0, 0, 0,
                30, 0, 8, 0, 0, false), new NavigationService(), s.worldMap());
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        s.dropDepthCharges("player-boat", "lookout", "boat");
        var releases = new java.util.LinkedHashMap<String, DepthChargeSnapshot>();
        for (int frame = 0; frame < 220; frame++) {
            s.snapshot().depthCharges().forEach(c -> releases.putIfAbsent(c.id(), c));
            tick(s, .05);
        }
        var shots = List.copyOf(releases.values());
        assertEquals(8, shots.size());
        assertEquals(4.8, shots.get(4).releasedAt(), .06);
        var lastByLane = new java.util.HashMap<Integer, Double>();
        for (var shot : shots) {
            var previous = lastByLane.put(shot.lane(), shot.releasedAt());
            if (previous != null) assertTrue(shot.releasedAt() - previous >= 4.8 - 1e-8);
        }
    }

    @Test void steadyForwardMotionProducesOverlappingDiamondRatherThanLongStrip() {
        var s = session();
        var navigation = new NavigationService();
        double speed = 17.5;
        s.applyPlayerState(new PlayerStateUpdate("player-boat", "light", 0, 0, 0,
                speed, 0, 8, 0, 0, false), navigation, s.worldMap());
        s.dropDepthCharges("player-boat", "player-boat", "boat");
        var released = new java.util.HashMap<Integer, DepthChargeSnapshot>();
        for (int frame = 0; frame <= 100; frame++) {
            double t = frame * .05;
            if (frame > 0) {
                s.applyPlayerState(new PlayerStateUpdate("player-boat", "light", 0, speed * t, 0,
                        speed, 0, 8, 0, t, false), navigation, s.worldMap());
                tick(s, .05);
            }
            s.snapshot().depthCharges().forEach(c -> released.putIfAbsent(c.lane(), c));
        }
        assertEquals(4, released.size());
        var first = released.get(0);
        var port = released.get(2);
        var starboard = released.get(3);
        var last = released.get(1);
        assertEquals(port.releasedAt(), starboard.releasedAt());
        assertEquals(port.z(), starboard.z(), .001);
        assertEquals(36, port.z() - first.z(), 1);
        assertEquals(36, last.z() - port.z(), 1);
        assertTrue(first.releasedAt() < port.releasedAt());
        assertTrue(last.releasedAt() > port.releasedAt());
        assertTrue(last.releasedAt() < 5);
    }

    @Test void sideThrowersRotateWithShipAndReachTargetsBeyondSternCoverage() {
        for (double heading : new double[]{0, Math.PI / 2, Math.PI, -Math.PI / 2}) {
            Vector2 origin = new Vector2(100, 200);
            Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
            Vector2 portTarget = origin.add(right.scale(-40));
            Vector2 starboardTarget = origin.add(right.scale(40));
            var boat = new ShipSetup("boat", "light", origin, heading, "player-boat", 2, 0, 999, "torpedo-boat", 0);
            var s = new GameSession(new GameSetup("throwers", new WorldMap(42, List.of()), List.of(
                    new FleetSetup("light", List.of(boat)),
                    new FleetSetup("dark", List.of(
                            ship("port", "dark", portTarget.x(), portTarget.z(), "submarine", -20),
                            ship("starboard", "dark", starboardTarget.x(), starboardTarget.z(), "submarine", -20)))),
                    List.of(new Vector2(1000, 1000))));
            s.dropDepthCharges("player-boat", "player-boat", "boat");
            tick(s, 3);
            assertFalse(s.snapshot().depthCharges().stream().anyMatch(c -> !c.targetShipIds().isEmpty()));
            tick(s, 2.5);
            var port = s.snapshot().depthCharges().stream().filter(c -> c.lane() == 2).findFirst().orElseThrow();
            assertEquals(List.of("port"), port.targetShipIds());
            var starboard = s.snapshot().depthCharges().stream().filter(c -> c.lane() == 3).findFirst().orElseThrow();
            assertEquals(List.of("starboard"), starboard.targetShipIds());
            assertEquals(2, s.snapshot().killsByPlayer().get("player-boat"));
        }
    }
}
