package one.xis.seabattle.game;

import one.xis.context.Service;
import one.xis.seabattle.webapp.admin.GameAdministration;
import java.util.List;

@Service
class DefaultGameAdministration implements GameAdministration {
    private static final List<SetupOption> SETUPS = List.of(
            new SetupOption("dense-land", "Inselgruppe mit Festland · normales Spiel"),
            new SetupOption("islands", "Offene Insellandschaft · normales Spiel"),
            new SetupOption("escort-debug", "Geleit-Testaufstellung"),
            new SetupOption("landmark-tour", "Landschaftsrundfahrt"),
            new SetupOption("dense-land-crowded", "Inselgruppe · dichte Flottenaufstellung"),
            new SetupOption("dense-land-crowded-reverse", "Inselgruppe · umgekehrte Flottenaufstellung"),
            new SetupOption("scout-plane", "Inselgruppe · Flugzeugaufstellung"),
            new SetupOption("side-view-sandbox", "Seitenansicht-Sandbox"),
            new SetupOption("two-ship-duel", "Zwei Schiffe"),
            new SetupOption("two-ship-duel-air", "Zwei Schiffe und Flugzeuge")
    );
    private final GameStateService game;

    DefaultGameAdministration(GameStateService game) { this.game = game; }
    @Override public List<SetupOption> setups() { return SETUPS; }
    @Override public String currentSetup() { return game.currentSetupId(); }
    @Override public void restart() { game.restartCurrentSetup(); }
    @Override public void selectSetup(String setupId) {
        if (SETUPS.stream().noneMatch(option -> option.id().equals(setupId)))
            throw new IllegalArgumentException("Unbekannte Aufstellung.");
        game.resetToSetup(setupId);
    }
}
