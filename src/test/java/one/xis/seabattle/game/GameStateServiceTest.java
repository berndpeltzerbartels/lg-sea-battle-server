package one.xis.seabattle.game;

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
                "player-BP-test", "light", "torpedo-boat", 0, 0, 0, 0, 0, 2, 0, -1, 1
        ));

        assertEquals(before.torpedoes().size() + 1, after.torpedoes().size());
        assertEquals(after.torpedoes().size(), service.snapshot().torpedoes().size());
    }

    @Test
    void freshServiceStartPublishesScoutPlanes() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );

        long planes = service.snapshot().ships().stream()
                .filter(ship -> "scout-plane".equals(ship.vehicleType()))
                .count();

        assertEquals(2, planes);
    }

    @Test
    void canResetToInlineScenarioForBrowserTests() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );

        GameSnapshot snapshot = service.resetToScenario(new ScenarioGameRequest("bernd", """
                scenario: inline-browser-test
                map:
                .1.2.
                objects:
                1: ship light human [orientation: 90°]
                2: plane dark bot [orientation: 270°, height: 110m]
                """));

        assertEquals(2, snapshot.ships().size());
        assertEquals("light-S1", snapshot.ships().get(0).id());
        assertEquals("dark-F2", snapshot.ships().get(1).id());
        assertEquals(110, snapshot.ships().get(1).y(), 0.001);
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
                playerId, teamId, "torpedo-boat", x, z, heading, 5, 0.1, 7, 12, 1, 2
        ));

        TorpedoSnapshot torpedo = after.torpedoes().get(after.torpedoes().size() - 1);
        assertEquals(x + 15, torpedo.x(), 1.0);
        assertEquals(z - 1.68, torpedo.z(), 0.2);
        assertEquals(heading, torpedo.heading(), 0.001);
    }

    @Test
    void fireTorpedoPublishesAcceptedTubeSide() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );

        GameSnapshot after = service.fireTorpedo(new FireTorpedoRequest(
                "player-BP-test", "light", "torpedo-boat", 0, 0, 0, 0, 0, 2, 0, -1, 1
        ));

        TorpedoSnapshot torpedo = after.torpedoes().get(after.torpedoes().size() - 1);
        assertEquals(-1, torpedo.tubeSide());
    }

    @Test
    void submarineDepthStateIsPublishedInSnapshot() {
        NavigationService navigationService = new NavigationService();
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
        session.assignPlayerVehicle("player-BP-test", "light", "submarine");

        GameSnapshot snapshot = session.updatePlayerState(new PlayerStateUpdate(
                "player-BP-test", "light", 12, 0, 0, 0, 0, 2, 0, 1, true,
                "submarine", 0, 0, null, null, null, null, "periscope"
        ), navigationService, session.worldMap());

        ShipSnapshot submarine = snapshot.ships().stream()
                .filter(ship -> "player-BP-test".equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        assertEquals("submarine", submarine.vehicleType());
        assertEquals("periscope", submarine.depthState());
    }

    @Test
    void fullySubmergedSubmarineCannotFireTorpedo() {
        NavigationService navigationService = new NavigationService();
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
        session.assignPlayerVehicle("player-BP-test", "light", "submarine");
        session.updatePlayerState(new PlayerStateUpdate(
                "player-BP-test", "light", 12, 0, 0, 0, 0, 2, 0, 1, true,
                "submarine", 0, 0, null, null, null, null, "submerged"
        ), navigationService, session.worldMap());

        GameSnapshot after = session.fireTorpedo(new FireTorpedoRequest(
                "player-BP-test", "light", "submarine", 12, 0, 0, 0, 0, 2, 0,
                0, 0, -1, 2, "submerged"
        ));

        assertEquals(0, after.torpedoes().size());
    }

    @Test
    void tickAdvancesIdleTimeWithoutConnectedPlayerWithoutMovingShips() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );

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
