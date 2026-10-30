package one.xis.seabattle.game;

/** Select one eligible target ID, or null for no target. One instance belongs to one game.
 * Detection and scheduling remain outside this strategy; candidates already satisfy existing rules.
 */
@FunctionalInterface
public interface BotTargetSelection {
    String select(BotTargetSituation situation, long tickId);

    static BotTargetSelection standard() {
        return (situation, tickId) -> situation.standardTargetId();
    }
}
