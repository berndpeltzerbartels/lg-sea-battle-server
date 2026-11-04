package one.xis.seabattle.game;

/** One vehicle's controls after target selection. Instances may keep per-game state. */
@FunctionalInterface
public interface VehicleBotCalculation<V extends Vehicle> {
  void calculate(V vehicle, BotSituation situation, long tickId, BotGoal goal);
}
