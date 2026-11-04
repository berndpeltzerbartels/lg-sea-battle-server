package one.xis.seabattle.game;

import java.util.Map;

/** Minimal independent controller. Deliberately omits obstacle/threat avoidance and weapons. */
public final class GoalFollowingCalculation implements BotCalculation {
  @Override
  public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
    for (Vehicle ship : situation.ships()) {
      BotGoal goal = goals.get(ship.id());
      if (goal == null || !"active".equals(ship.state())) continue;
      if (goal.kind() == BotGoal.Kind.NONE) {
        situation.steer(ship, 2, 0);
        continue;
      }
      double heading = Math.atan2(goal.x() - ship.position().x(), goal.z() - ship.position().z());
      double error =
          Math.atan2(Math.sin(heading - ship.heading()), Math.cos(heading - ship.heading()));
      int rudder = (int) Math.round(Math.max(-35, Math.min(35, Math.toDegrees(error))));
      situation.steer(ship, 5, rudder);
    }
  }
}
