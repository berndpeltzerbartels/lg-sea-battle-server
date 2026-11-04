package one.xis.seabattle.game;

/** Minimal replacement example. Intentionally has no targeting, evasion or weapons. */
public final class StraightAheadCalculation implements BotCalculation {
  @Override
  public void calculate(BotSituation situation, long tickId, java.util.Map<String, BotGoal> goals) {
    for (Vehicle vehicle : situation.vehicles()) {
      if ("bot".equals(vehicle.controlledBy()) && "active".equals(vehicle.state())) {
        situation.steer(vehicle, 8, 0);
      }
    }
  }
}
