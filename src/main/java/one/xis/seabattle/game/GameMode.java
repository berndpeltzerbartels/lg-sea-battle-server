package one.xis.seabattle.game;

/** Fixed for the lifetime of a round. */
public enum GameMode {
    ENDLESS, BURN_DOWN;

    public boolean replacesLosses() { return this == ENDLESS; }
    public boolean defeated(int torpedoBoats, int humans) {
        return this == BURN_DOWN && (torpedoBoats == 0 || 4L * torpedoBoats < humans);
    }
}
