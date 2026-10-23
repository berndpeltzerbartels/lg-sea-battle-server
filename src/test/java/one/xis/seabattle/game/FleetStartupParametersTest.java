package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class FleetStartupParametersTest {
    @Test
    void configuredFleetSizeSurvivesRoundEndAndRestart() {
        var game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
        var before = game.snapshot();
        int expected = Boolean.getBoolean("seaBattle.localFleetTrial") ? 20 : 15;
        var fleetSizes = before.ships().stream().collect(Collectors.groupingBy(ShipSnapshot::teamId, Collectors.counting()));
        assertEquals(2, fleetSizes.size());
        fleetSizes.values().forEach(size -> assertEquals(expected, size.intValue()));
        game.finishIfNoPlayers();
        assertEquals("finished", game.snapshot().state());
        var restarted = game.resetCurrentSetup();
        assertEquals("running", restarted.state());
        assertEquals(before.sessionId(), restarted.sessionId());
        assertEquals(fleetSizes, restarted.ships().stream().collect(Collectors.groupingBy(ShipSnapshot::teamId, Collectors.counting())));
        assertEquals(before.ships().stream().collect(Collectors.groupingBy(ShipSnapshot::vehicleType, Collectors.counting())),
                restarted.ships().stream().collect(Collectors.groupingBy(ShipSnapshot::vehicleType, Collectors.counting())));
    }

    @Test
    void finishedCustomScenarioRestartsWithItsOriginalVehiclesAndMap() {
        var game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
        var before = game.resetToScenario(new ScenarioGameRequest("bernd", """
                scenario: restart-test
                map:
                .1.2.
                objects:
                1: ship light human [orientation: 90°]
                2: plane dark bot [orientation: 270°, height: 110m]
                """));
        var world = game.worldMap();
        game.finishIfNoPlayers();
        var after = game.resetCurrentSetup();
        assertEquals("running", after.state());
        assertEquals(before.sessionId(), after.sessionId());
        assertEquals(before.ships(), after.ships());
        assertEquals(world, game.worldMap());
    }

    @Test
    void finishingWithoutPlayersCannotEndAnOccupiedRound() {
        var game = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(), new NavigationService());
        game.connectPlayer("player-CAP-test");
        game.finishIfNoPlayers();
        assertEquals("running", game.snapshot().state());
    }
}
