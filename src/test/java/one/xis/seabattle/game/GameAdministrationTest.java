package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameAdministrationTest {
    private GameStateService game() {
        return new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), new RadarService(),
                new NavigationService(), TestGameConfigService.defaults());
    }

    @Test void allListedSetupsExistAndUnknownSelectionsLeaveTheGameUntouched() {
        var game = game();
        var administration = new DefaultGameAdministration(game);
        var factory = new DefaultGameSetupFactory(new WorldMapService());
        for (var option : administration.setups()) assertNotNull(factory.setup(option.id()));
        String previous = game.snapshot().instanceId();
        assertThrows(IllegalArgumentException.class, () -> administration.selectSetup("unknown"));
        assertEquals(previous, game.snapshot().instanceId());
        administration.selectSetup("two-ship-duel");
        assertEquals("two-ship-duel", administration.currentSetup());
        assertEquals("two-ship-duel", game.snapshot().sessionId());
        previous = game.snapshot().instanceId();
        administration.restart();
        assertNotEquals(previous, game.snapshot().instanceId());
        assertEquals("two-ship-duel", game.snapshot().sessionId());
    }

    @Test void roundModeSurvivesRestartAndAutomaticLoginCannotResetTheRound() {
        var game = game();
        var administration = new DefaultGameAdministration(game);
        administration.start("two-ship-duel", "BURN_DOWN");
        assertEquals(GameMode.BURN_DOWN, game.snapshot().mode());
        var previous = game.snapshot().instanceId();
        game.resetCurrentSetup();
        assertEquals(previous,game.snapshot().instanceId());
        administration.restart();
        assertNotEquals(previous,game.snapshot().instanceId());
        assertEquals("BURN_DOWN",administration.currentMode());
        administration.start("two-ship-duel", "ENDLESS");
        assertEquals(GameMode.ENDLESS,game.snapshot().mode());
    }

    @Test void anExplicitRestartAlsoRestartsARunningTextScenario() {
        var game = game();
        game.resetToScenario(new ScenarioGameRequest("bernd", """
                scenario: administration-test
                map:
                .1.2.
                objects:
                1: ship light bot
                2: ship dark human
                """));
        var before = game.snapshot();
        game.resetCurrentSetup();
        assertEquals(before.instanceId(), game.snapshot().instanceId(), "Automatic join must not restart a running scenario");
        new DefaultGameAdministration(game).restart();
        assertNotEquals(before.instanceId(), game.snapshot().instanceId());
        assertEquals("administration-test", game.snapshot().sessionId());
    }
}
