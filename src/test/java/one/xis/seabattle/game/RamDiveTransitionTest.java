package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RamDiveTransitionTest {
    @Test void diveCommandDoesNotProtectAnExposedSubmarine() {
        for (String command : List.of("surface", "periscope", "submerged")) {
            for (double y : new double[] { -.78, -5.58, -6.5 }) {
                GameSnapshot result = collide(command, y);
                assertEquals("sunk", result.ships().stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow().state(), command + " y=" + y);
                var attacker = result.ships().stream().filter(s -> s.id().equals("boat")).findFirst().orElseThrow();
                assertEquals("active", attacker.state());
                assertEquals(0, attacker.speed());
                assertEquals(2, attacker.engineOrder());
                assertEquals("boat", result.ramHits().get(0).shipId());
                assertEquals("sub", result.ramHits().get(0).targetShipId());
            }
        }
    }

    @Test void physicallyDeepSubmarineIsSafeEvenAfterSurfacingCommand() {
        for (String command : List.of("surface", "periscope", "submerged")) {
            GameSnapshot result = collide(command, -19.26);
            assertTrue(result.ships().stream().allMatch(s -> s.state().equals("active")));
            assertTrue(result.ramHits().isEmpty());
            assertEquals(8, result.ships().stream().filter(s -> s.id().equals("boat")).findFirst().orElseThrow().speed());
        }
    }

    private GameSnapshot collide(String command, double y) {
        var world = new WorldMap(9012, List.of());
        var navigation = new NavigationService();
        var session = new GameSession(new GameSetup("ram-depth", world, List.of(
                new FleetSetup("light", List.of(new ShipSetup("boat", "light", new Vector2(0, -11.04), 0, "player-boat", 7, 0, 99, "torpedo-boat", 0))),
                new FleetSetup("dark", List.of(new ShipSetup("sub", "dark", new Vector2(0, 0), 0, "player-sub", 2, 0, 99, "submarine", y)))
        ), List.of(new Vector2(0, -11.04), new Vector2(0, 0))));
        session.updatePlayerState(new PlayerStateUpdate("player-boat", "light", 0, -11.04, 0, 8, 0, 7, 0, 0, true, "torpedo-boat"), navigation, world);
        session.updatePlayerState(new PlayerStateUpdate("player-sub", "dark", 0, 0, 0, 0, 0, 2, 0, 0, true,
                "submarine", y, 0, null, null, null, null, command), navigation, world);
        session.update(0, new RadarService(), navigation, world);
        return session.snapshot();
    }
}
