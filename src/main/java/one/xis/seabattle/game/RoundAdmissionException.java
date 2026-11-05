package one.xis.seabattle.game;

/** The round is closed or its remaining torpedo boats have no room for another human. */
public final class RoundAdmissionException extends IllegalArgumentException {
    public RoundAdmissionException() {
        super("In dieser Runde ist kein freier Bootsplatz für diese Partei verfügbar. Bitte die nächste Runde abwarten oder die andere Partei wählen.");
    }
}
