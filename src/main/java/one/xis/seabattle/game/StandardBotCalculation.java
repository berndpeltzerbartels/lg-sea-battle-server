package one.xis.seabattle.game;

import java.util.Map;
import java.util.Objects;

/** Dispatches each bot to its typed calculation; contains no vehicle-specific decisions. */
public final class StandardBotCalculation implements BotCalculation {
  private final VehicleBotCalculation<TorpedoBoat> torpedoBoats;
  private final VehicleBotCalculation<Submarine> submarines;
  private final VehicleBotCalculation<ScoutPlane> planes;
  private final WaterBotNavigation waterNavigation;

  public StandardBotCalculation() {
    waterNavigation = new WaterBotNavigation();
    torpedoBoats = new TorpedoBoatBotCalculation(waterNavigation);
    submarines = new SubmarineBotCalculation(waterNavigation);
    planes = new ScoutPlaneBotCalculation();
  }

  /** Replace individual vehicle algorithms, without replacing the whole simulation round. */
  public StandardBotCalculation(
      VehicleBotCalculation<TorpedoBoat> torpedoBoats,
      VehicleBotCalculation<Submarine> submarines,
      VehicleBotCalculation<ScoutPlane> planes) {
    this.torpedoBoats = Objects.requireNonNull(torpedoBoats, "torpedoBoats");
    this.submarines = Objects.requireNonNull(submarines, "submarines");
    this.planes = Objects.requireNonNull(planes, "planes");
    waterNavigation = null;
  }

  @Override
  public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
    try {
      if (waterNavigation != null) waterNavigation.beginRound(situation);
      for (Vehicle vehicle : situation.vehicles()) {
        if (!vehicle.isBotControlled()) continue;
        BotGoal goal =
            Objects.requireNonNull(goals.get(vehicle.id()), "Missing bot goal: " + vehicle.id());
        if (vehicle instanceof TorpedoBoat boat)
          torpedoBoats.calculate(boat, situation, tickId, goal);
        else if (vehicle instanceof Submarine submarine)
          submarines.calculate(submarine, situation, tickId, goal);
        else if (vehicle instanceof ScoutPlane plane)
          planes.calculate(plane, situation, tickId, goal);
      }
      if (waterNavigation != null) waterNavigation.finishRound(situation);
    } finally {
      if (waterNavigation != null) waterNavigation.clearRound();
    }
  }
}
