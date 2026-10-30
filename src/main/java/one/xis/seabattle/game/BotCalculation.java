package one.xis.seabattle.game;

/** One bot decision round. Physics and combat resolution remain the responsibility of GameSession.
 * Implementations may keep per-session state; create a fresh instance for every independent run.
 */
@FunctionalInterface
public interface BotCalculation {
    void calculate(BotSituation situation, long tickId);

    /** The unchanged production bot algorithm. Can also be called from a custom implementation. */
    static BotCalculation standard() {
        return (situation, tickId) -> situation.runStandard();
    }
}
