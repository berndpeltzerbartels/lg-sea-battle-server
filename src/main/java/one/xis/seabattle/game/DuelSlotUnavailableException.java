package one.xis.seabattle.game;

public final class DuelSlotUnavailableException extends IllegalArgumentException {
    public DuelSlotUnavailableException() {
        super("Spezialmenü 9 hat ein Schiff pro Farbe. Bitte die andere Farbe wählen oder den Wiedereinstieg des gesunkenen Schiffs abwarten.");
    }
}
