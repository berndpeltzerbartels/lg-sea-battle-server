package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UnderwaterTorpedoTest {
    private static final double SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double DEEP = -3.21 * SCALE;

    @Test
    void wallImpactIsRecordedAtWaterSideAndPreservesLaunchDepth() {
        for (double depth : new double[]{0, DEEP}) {
            WorldMap map = new WorldMap(99003, List.of(new Landmass("island", "wall", 0, 200,
                    100, 80, 100, 80, 120, 96, null, 1, null, null, null,
                    List.of(), List.of(), List.of())));
            NavigationService navigation = new NavigationService();
            GameSession session = new GameSession(new GameSetup("wall-torpedo", map, List.of(
                    new FleetSetup("light", List.of(new ShipSetup("shooter", "light", new Vector2(0, 0),
                            0, "diver", 2, 0, 0, "submarine", depth)))), List.of()));
            session.updatePlayerState(new PlayerStateUpdate("diver", "light", 0, 0, 0, 0, 0, 2, 0, 0,
                    false, "submarine", depth, 0, null, null, null, null,
                    depth == 0 ? "surface" : "submerged"), navigation, map);
            session.fireTorpedo(new FireTorpedoRequest("diver", "light", "submarine"));
            double y = session.snapshot().torpedoes().get(0).y();
            TorpedoImpactSnapshot impact = null;
            for (int tick = 0; tick < 200 && impact == null; tick++) {
                session.update(.1, new RadarService(), navigation, map);
                if (!session.snapshot().torpedoImpacts().isEmpty()) {
                    impact = session.snapshot().torpedoImpacts().get(0);
                }
            }
            assertNotNull(impact);
            assertEquals("land-hit", impact.reason());
            assertEquals(y, impact.y(), .001);
            assertNull(impact.targetShipId());
            // Snapshot rounding may straddle the boundary by a millimetre.
            assertFalse(navigation.isTorpedoBlocked(new Vector2(impact.x(), impact.z() - .01), map));
            assertTrue(navigation.isTorpedoBlocked(new Vector2(impact.x(), impact.z() + .02), map));
        }
    }

    @Test
    void deepTorpedoHitsOnlySubmarineAtItsOwnDepth() {
        for (String type : List.of("torpedo-boat", "submarine")) {
            for (double depth : new double[]{0, -1.86 * SCALE, DEEP, DEEP - 2 * SCALE}) {
                WorldMap map = new WorldMap(99002, List.of());
                NavigationService navigation = new NavigationService();
                GameSession session = new GameSession(new GameSetup("deep-torpedo", map, List.of(
                        new FleetSetup("light", List.of(new ShipSetup("shooter", "light", new Vector2(0, 0),
                                0, "diver", 2, 0, 0, "submarine", DEEP))),
                        new FleetSetup("dark", List.of(new ShipSetup("target", "dark", new Vector2(0, 100),
                                0, "target-player", 2, 0, 0, type, depth)))), List.of()));
                session.updatePlayerState(new PlayerStateUpdate("diver", "light", 0, 0, 0, 0, 0, 2, 0, 0,
                        false, "submarine", DEEP, 0, null, null, null, null, "submerged"), navigation, map);
                session.updatePlayerState(new PlayerStateUpdate("target-player", "dark", 0, 100, 0, 0, 0, 2, 0, 0,
                        false, type, depth, 0, null, null, null, null,
                        depth == 0 ? "surface" : depth > DEEP ? "periscope" : "submerged"), navigation, map);
                session.fireTorpedo(new FireTorpedoRequest("diver", "light", "submarine"));
                assertEquals(1, session.snapshot().torpedoes().size());
                double torpedoY = session.snapshot().torpedoes().get(0).y();
                assertEquals(DEEP + .2 * SCALE, torpedoY, .001);
                session.fireTorpedo(new FireTorpedoRequest("diver", "light", "submarine"));
                assertEquals(1, session.snapshot().torpedoes().size(), "Cooldown still applies");
                session.updatePlayerState(new PlayerStateUpdate("diver", "light", 0, 0, 0, 0, 0, 2, 0, 0,
                        false, "submarine", 0, 0, null, null, null, null, "surface"), navigation, map);
                boolean hit = false;
                for (int i = 0; i < 150; i++) {
                    session.update(.1, new RadarService(), navigation, map);
                    for (var torpedo : session.snapshot().torpedoes()) assertEquals(torpedoY, torpedo.y(), .001);
                    if (!session.snapshot().torpedoImpacts().isEmpty()) {
                        var impact = session.snapshot().torpedoImpacts().get(0);
                        hit = "target".equals(impact.targetShipId());
                        assertEquals(torpedoY, impact.y(), .001);
                        break;
                    }
                }
                assertEquals(type.equals("submarine") && depth == DEEP, hit, type + " depth=" + depth);
            }
        }
    }
}
