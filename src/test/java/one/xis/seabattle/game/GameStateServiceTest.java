package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void playerStateUpdateIsPublishedImmediatelyForReload() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        GameSnapshot setup = service.resetToScenario(new ScenarioGameRequest("bernd", """
                scenario: reload-submarine-sync-test
                map:
                .....
                ..1..
                .....
                objects:
                1: submarine light human [orientation: 90°, speed: 0knt]
                """));
        String playerId = setup.ships().get(0).controlledBy();

        GameSnapshot afterUpdate = service.updatePlayerState(new PlayerStateUpdate(
                playerId, "light", 48, 12, Math.PI / 2, 6, 0.08, 7, 4, 1, false,
                "submarine", -0.6, 0, null, null, null, null, "periscope"
        ));
        GameSnapshot afterImmediateReloadRead = service.snapshot();

        ShipSnapshot returnedSubmarine = afterUpdate.ships().stream()
                .filter(ship -> playerId.equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        ShipSnapshot reloadedSubmarine = afterImmediateReloadRead.ships().stream()
                .filter(ship -> playerId.equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        assertEquals(48, returnedSubmarine.x(), 0.001);
        assertEquals(12, returnedSubmarine.z(), 0.001);
        assertEquals(6, returnedSubmarine.speed(), 0.001);
        assertEquals(7, returnedSubmarine.engineOrder());
        assertEquals(4, returnedSubmarine.rudderDegrees());
        assertEquals("submarine", returnedSubmarine.vehicleType());
        assertEquals("periscope", returnedSubmarine.depthState());
        assertEquals(returnedSubmarine.x(), reloadedSubmarine.x(), 0.001);
        assertEquals(returnedSubmarine.z(), reloadedSubmarine.z(), 0.001);
        assertEquals(returnedSubmarine.speed(), reloadedSubmarine.speed(), 0.001);
        assertEquals(returnedSubmarine.engineOrder(), reloadedSubmarine.engineOrder());
        assertEquals(returnedSubmarine.depthState(), reloadedSubmarine.depthState());
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
                "submarine", -0.72, 0, null, null, null, null, "periscope"
        ), navigationService, session.worldMap());

        ShipSnapshot submarine = snapshot.ships().stream()
                .filter(ship -> "player-BP-test".equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        assertEquals("submarine", submarine.vehicleType());
        assertEquals("periscope", submarine.depthState());
        assertEquals(-0.72, submarine.y(), 0.001);
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
    void submarineDepthControlsRadarVisibility() {
        RadarService radarService = new RadarService();
        NavigationService navigationService = new NavigationService();
        WorldMap worldMap = new WorldMap(9020, List.of());
        Ship surfaceShip = new Ship("red-1", "red", new Vector2(0, 0), 0, "scenario");
        Ship scoutPlane = new Ship("red-plane", "red", new Vector2(0, 0), 0, "scenario");
        scoutPlane.vehicleType("scout-plane");
        Ship submarine = new Ship("blue-1", "blue", new Vector2(80, 0), 0, "player-blue");

        submarine.applyPlayerState(new PlayerStateUpdate(
                "player-blue", "blue", 80, 0, 0, 0, 0, 2, 0, 0, true,
                "submarine", 0, 0, null, null, null, null, "periscope"
        ), navigationService, worldMap);

        assertFalse(radarService.isVisible(surfaceShip, submarine, worldMap));
        assertTrue(radarService.isVisible(scoutPlane, submarine, worldMap));

        submarine.applyPlayerState(new PlayerStateUpdate(
                "player-blue", "blue", 80, 0, 0, 0, 0, 2, 0, 0, true,
                "submarine", 0, 0, null, null, null, null, "submerged"
        ), navigationService, worldMap);

        assertFalse(radarService.isVisible(surfaceShip, submarine, worldMap));
        assertFalse(radarService.isVisible(scoutPlane, submarine, worldMap));
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

    @Test
    void tickContinuesHumanShipFromLastCommand() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        String playerId = "player-BP-test";
        service.connectPlayer(playerId);
        service.assignPlayerVehicle(playerId, "light", "submarine");
        service.updatePlayerState(new PlayerStateUpdate(
                playerId, "light", 0, 0, 0, 6, 0, 7, 0, 1, true,
                "submarine", -0.6, 0, null, null, null, null, "periscope"
        ));

        GameSnapshot after = service.tick(1);
        ShipSnapshot afterShip = after.ships().stream()
                .filter(ship -> playerId.equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();

        assertTrue(afterShip.z() > 0);
        assertEquals("periscope", afterShip.depthState());
        assertEquals(-0.6, afterShip.y(), 0.001);
    }
}
