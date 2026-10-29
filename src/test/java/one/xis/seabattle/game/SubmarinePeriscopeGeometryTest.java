package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubmarinePeriscopeGeometryTest {
    private Ship submarine(double heading) {
        Ship ship = new Ship("sub", "dark", new Vector2(0, 0), heading, "captain");
        ship.applyPlayerState(new PlayerStateUpdate("captain", "dark", 0, 0, heading, 0, 0, 2, 0, 0,
                true, "submarine", -5.58, 0, null, null, null, null, "periscope"),
                new NavigationService(), new WorldMap(1, List.of()));
        return ship;
    }
    @Test void trialMakesTheWholeMastAndItsExtensionThirtyPercentLarger() {
        double oldHeight = .986 * .9 + (SubmarinePeriscopeGeometry.BASE + .986 * .9 - 1.86) * .2;
        double newHeight = SubmarinePeriscopeGeometry.topAboveRoot(-5.58) / 3 - SubmarinePeriscopeGeometry.BASE;
        assertEquals(oldHeight * 1.3, newHeight, 1e-10);
        assertEquals(.019 * 1.3, SubmarinePeriscopeGeometry.RADIUS, 1e-10);
        assertEquals(0, SubmarinePeriscopeGeometry.lift(-.78), 1e-10);
        assertEquals(0, SubmarinePeriscopeGeometry.lift(-19.26), 1e-10);
    }
    @Test void fastShotsHitTheExtendedTipAndTheTrialWidthButMissOutsideIt() {
        Ship ship = submarine(0);
        double y = -5.58 + (SubmarinePeriscopeGeometry.TOP + SubmarinePeriscopeGeometry.EXTRA_LIFT / 2) * 3;
        double z = .022 * 3;
        double hit = SubmarinePeriscopeGeometry.hitParameter(ship, -10, y, z, 10, y, z);
        assertTrue(Double.isFinite(hit));
        assertTrue(hit > .49 && hit < .51);
        assertTrue(Double.isFinite(SubmarinePeriscopeGeometry.hitParameter(ship, -10, y, z + .065, 10, y, z + .065)),
                "New outer rim is genuinely hittable rather than just drawn thicker");
        assertFalse(Double.isFinite(SubmarinePeriscopeGeometry.hitParameter(ship, -10, y, z + SubmarinePeriscopeGeometry.RADIUS * 3 + .001, 10, y, z + SubmarinePeriscopeGeometry.RADIUS * 3 + .001)));
        double above = -5.58 + SubmarinePeriscopeGeometry.topAboveRoot(-5.58) + .001;
        assertFalse(Double.isFinite(SubmarinePeriscopeGeometry.hitParameter(ship, -10, above, z, 10, above, z)));
    }
    @Test void rotatedMastAndVerticalShotsUseTheSameVisibleGeometry() {
        Ship ship = submarine(Math.PI / 2);
        double x = .022 * 3;
        assertTrue(Double.isFinite(SubmarinePeriscopeGeometry.hitParameter(ship, x, 2, 0, x, -4, 0)));
        assertFalse(Double.isFinite(SubmarinePeriscopeGeometry.hitParameter(ship, x, 2, .08, x, -4, .08)));
    }
}
