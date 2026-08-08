package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateServiceTest {

    @Test
    void fireTorpedoReturnsFreshSnapshot() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        GameSnapshot before = service.snapshot();

        GameSnapshot after = service.fireTorpedo(new FireTorpedoRequest(
                "player-BP-test", "light", "torpedo-boat", 0, 0, 0, 0, 0, 2, 0, 1
        ));

        assertEquals(before.torpedoes().size() + 1, after.torpedoes().size());
        assertEquals(after.torpedoes().size(), service.snapshot().torpedoes().size());
    }

    @Test
    void fireTorpedoUsesNavigationStateFromFireRequest() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        service.resetToSetup("ram-side");
        String playerId = "player-red-test";
        String teamId = "red";
        double x = 2;
        double z = 0;
        service.updatePlayerState(new PlayerStateUpdate(
                playerId, teamId, x, z, 0, 0, 0, 2, 0, 1, false, "torpedo-boat"
        ));

        double heading = Math.PI / 2;
        GameSnapshot after = service.fireTorpedo(new FireTorpedoRequest(
                playerId, teamId, "torpedo-boat", x, z, heading, 5, 0.1, 7, 12, 2
        ));

        TorpedoSnapshot torpedo = after.torpedoes().get(after.torpedoes().size() - 1);
        assertEquals(x + 5, torpedo.x(), 1.0);
        assertEquals(z, torpedo.z(), 1.0);
        assertEquals(heading, torpedo.heading(), 0.001);
    }

    @Test
    void tickDoesNotAdvanceWithoutConnectedPlayer() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );

        GameSnapshot before = service.snapshot();
        GameSnapshot after = service.tick(1);

        assertEquals(before.t(), after.t());
    }

    @Test
    void tickAdvancesIdleTimeForConnectedPlayerWithoutMovingShips() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        service.connectPlayer("player-BP-test");

        GameSnapshot before = service.snapshot();
        ShipSnapshot beforeShip = before.ships().get(0);
        GameSnapshot after = service.tick(1);
        ShipSnapshot afterShip = after.ships().stream()
                .filter(ship -> ship.id().equals(beforeShip.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(after.t() > before.t());
        assertEquals(beforeShip.x(), afterShip.x());
        assertEquals(beforeShip.z(), afterShip.z());
    }
}
