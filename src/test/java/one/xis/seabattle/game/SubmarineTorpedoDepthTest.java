package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubmarineTorpedoDepthTest {
    private static final List<String> STATES = List.of("surface", "periscope", "submerged");
    private static final double WATER_Y = .05;
    private static final double MAST_Z = .022 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private final WorldMap water = new WorldMap(1, List.of());
    private final NavigationService navigation = new NavigationService();

    private void depth(Vehicle sub, double y, String state) {
        sub.applyPlayerState(new PlayerStateUpdate("diver", "dark", 0, 0, 0, 0, 0, 2, 0, 0,
                false, "submarine", y, 0, null, null, null, null, state), navigation, water);
    }

    private Torpedo crossing(double z) {
        return new Torpedo("torpedo", "light", "shooter", new Vector2(-10, z), Math.PI / 2, 100, 0, 100);
    }

    private boolean hit(GameSession session, Vehicle sub, double z) throws Exception {
        Torpedo torpedo = crossing(z);
        torpedo.update(.2);
        Method intersects = GameSession.class.getDeclaredMethod("torpedoHitsShip", Torpedo.class, Vehicle.class);
        intersects.setAccessible(true);
        return session.torpedoCanDamageShip(torpedo, sub) && (boolean) intersects.invoke(session, torpedo, sub);
    }

    @Test void realDepthDecidesEvenWhenTheSelectedOrReportedStateDisagrees() throws Exception {
        var session = new GameSession(new GameSetup("depth-versus-status", water, List.of(), List.of()));
        var sub = new Submarine("sub", "dark", new Vector2(0, 0), 0, "diver");
        for (String state : STATES) {
            depth(sub, -.78, state);
            assertTrue(hit(session, sub, 3), "A physically surfaced hull remains hittable: " + state);
            for (double y : new double[]{-5.58, -6.2, -6.8}) {
                depth(sub, y, state);
                assertTrue(hit(session, sub, MAST_Z), "Exact exposed mast hit: y=" + y + " state=" + state);
                assertFalse(hit(session, sub, 3), "No imaginary surface hull: y=" + y + " state=" + state);
            }
            depth(sub, -7.3, state);
            assertFalse(hit(session, sub, MAST_Z), "Mast has actually passed below the surface torpedo");
            depth(sub, SeaBattleGameConfig.SUBMARINE_DEEP_Y, state);
            assertFalse(hit(session, sub, MAST_Z), "A surface command must not expose a deeply submerged boat");
        }
    }

    @Test void descendingMastStopsBeingHittableAtItsActualTipNotAtAStateChange() throws Exception {
        var session = new GameSession(new GameSetup("descending-mast", water, List.of(), List.of()));
        var sub = new Submarine("sub", "dark", new Vector2(0, 0), 0, "diver");
        for (String state : STATES) {
            for (double y = -5.58; y >= -8; y -= .02) {
                depth(sub, y, state);
                boolean exposed = y + SubmarinePeriscopeGeometry.topAboveRoot(y) >= WATER_Y;
                assertEquals(exposed, hit(session, sub, MAST_Z), "Tip crossing: y=" + y + " state=" + state);
                assertFalse(hit(session, sub, MAST_Z + SubmarinePeriscopeGeometry.RADIUS * 3 + .001),
                        "A close pass beside the mast is not a direct hit");
            }
        }
    }

    @Test void actualCollisionRoundRecordsOnlyPhysicalHitsDuringTheDive() throws Exception {
        for (String state : STATES) for (double y : new double[]{-5.58, -6.2, -6.8, -7.3}) {
            for (boolean aimedAtMast : List.of(false, true)) {
                var setup = new GameSetup("torpedo-during-descent", water, List.of(
                        new FleetSetup("light", List.of(new VehicleSetup("shooter", "light", new Vector2(-100, 0),
                                0, "captain", 2, 0, 9999, "torpedo-boat", 0))),
                        new FleetSetup("dark", List.of(new VehicleSetup("sub", "dark", new Vector2(0, 0),
                                0, "diver", 2, 0, 9999, "submarine", y)))), List.of());
                var session = new GameSession(setup);
                Vehicle sub = session.calculationShips().stream().filter(v -> v.id().equals("sub")).findFirst().orElseThrow();
                depth(sub, y, state);
                Field field = GameSession.class.getDeclaredField("torpedoes");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") List<Torpedo> torpedoes = (List<Torpedo>) field.get(session);
                torpedoes.add(crossing(aimedAtMast ? MAST_Z : 3));
                session.update(.2, new RadarService(), navigation, water);
                boolean expected = aimedAtMast && y >= -6.8;
                assertEquals(expected, !"active".equals(sub.state()), "y=" + y + " state=" + state + " mast=" + aimedAtMast);
                assertEquals(expected, session.snapshot().torpedoImpacts().stream()
                        .anyMatch(impact -> "ship-hit".equals(impact.reason()) && "sub".equals(impact.targetShipId())));
            }
        }
    }
}
