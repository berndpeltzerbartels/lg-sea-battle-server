package one.xis.seabattle.game;

import java.util.Map;

/** Minimal independent controller. Deliberately omits obstacle/threat avoidance and weapons. */
public final class GoalFollowingCalculation implements BotCalculation {
  @Override
  public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
    for (Vehicle vehicle : situation.vehicles()) {
      BotGoal goal = goals.get(vehicle.id());
      if (goal == null || !"active".equals(vehicle.state())) continue;
      if (goal.kind() == BotGoal.Kind.NONE) {
        situation.steer(vehicle, 2, 0);
        continue;
      }
      double heading =
          Math.atan2(goal.x() - vehicle.position().x(), goal.z() - vehicle.position().z());
      double error =
          Math.atan2(Math.sin(heading - vehicle.heading()), Math.cos(heading - vehicle.heading()));
      int rudder = (int) Math.round(Math.max(-35, Math.min(35, Math.toDegrees(error))));
      situation.steer(vehicle, 5, rudder);
    }
  }
}
