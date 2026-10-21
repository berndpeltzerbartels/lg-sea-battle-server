package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportedShoreCollisionTest {
    @Test
    void october21PositionsAreGroundedAccordingToServerGeometry() {
        var world = new WorldMap(1, List.of(
                new Landmass("coastline", "western_coast", -126, 58, 88, 76, 88, 76, 88, 76,
                        null, 1, null, .22, null, List.of(), List.of(), List.of()),
                new Landmass("coastline", "mist_gate_north", 640, -850, 96, 62, 96, 62, 96, 62,
                        null, 1.24, 24.0, .24, null, List.of(), List.of(), List.of())));
        var navigation = new NavigationService();
        assertTrue(navigation.isShipMovementBlocked(new Vector2(-215.50385549297624, 21.86715346294695), -.658, .58, world));
        assertTrue(navigation.isShipMovementBlocked(new Vector2(563.2729191173329, -786.8845655181863), 1.717, .9, world));
    }
}
