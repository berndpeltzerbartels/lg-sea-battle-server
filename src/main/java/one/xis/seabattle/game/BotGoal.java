package one.xis.seabattle.game;

/** Immutable navigation destination at this tick. Key in the goal map is the observer's ship ID.
 * Hidden enemies contribute only remembered positions, never their current coordinates.
 */
public record BotGoal(Kind kind, String targetShipId, long targetLifeId, double x, double z) {
    public enum Kind { ENEMY, LAST_KNOWN_COURSE, SEARCH, RETAINED_COURSE, PATROL, NONE }
}
