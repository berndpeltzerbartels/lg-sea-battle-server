package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalFleetTrialTest {
    @Test
    void localTrialAddsTheRequestedMixAndLeavesNormalSetupUnchanged() {
        String previous = System.getProperty("seaBattle.localFleetTrial");
        try {
            System.clearProperty("seaBattle.localFleetTrial");
            var factory = new DefaultGameSetupFactory(new WorldMapService());
            var normal = factory.defaultSetup();
            assertTrue(normal.fleets().stream().allMatch(f -> f.ships().size() == 15));
            System.setProperty("seaBattle.localFleetTrial", "true");
            var trial = factory.defaultSetup();
            var navigation = new NavigationService();
            for (var fleet : trial.fleets()) {
                assertEquals(20, fleet.ships().size());
                assertEquals(4, fleet.ships().stream().filter(s -> "submarine".equals(s.vehicleType())).count());
                assertEquals(2, fleet.ships().stream().filter(s -> "scout-plane".equals(s.vehicleType())).count());
                assertEquals(14, fleet.ships().stream().filter(s -> "torpedo-boat".equals(s.vehicleType())).count());
                for (var ship : fleet.ships().subList(15, 20)) {
                    if (!"scout-plane".equals(ship.vehicleType())) {
                        assertFalse(navigation.isShipBlocked(ship.position(), ship.heading(), trial.worldMap()), ship.id());
                    }
                }
            }
        } finally {
            if (previous == null) System.clearProperty("seaBattle.localFleetTrial");
            else System.setProperty("seaBattle.localFleetTrial", previous);
        }
    }
}
