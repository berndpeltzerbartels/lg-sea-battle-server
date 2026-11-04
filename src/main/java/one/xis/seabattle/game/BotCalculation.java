package one.xis.seabattle.game;

import java.util.Map;

/**
 * One movement/control round, after target selection has completed for all bots. Physics and combat
 * resolution remain the responsibility of GameSession. Implementations may keep per-session state;
 * create a fresh instance for every independent run.
 */
@FunctionalInterface
public interface BotCalculation {
  void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals);

  /** Existing movement/combat controls, consuming the already prepared goals. */
  static BotCalculation standard() {
    return new StandardBotCalculation();
  }
}
