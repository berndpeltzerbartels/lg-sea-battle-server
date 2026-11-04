package one.xis.seabattle.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Routes target-policy calls to the concrete vehicle type; contains no target decisions. */
public final class StandardBotTargetSelection implements BotTargetSelection {
  private final VehicleBotTargetSelection<TorpedoBoat> torpedoBoats;
  private final VehicleBotTargetSelection<Submarine> submarines;
  private final VehicleBotTargetSelection<ScoutPlane> planes;

  public StandardBotTargetSelection() {
    var waterTargets = new WaterBotTargetSelection();
    torpedoBoats = new TorpedoBoatBotTargetSelection(waterTargets);
    submarines = new SubmarineBotTargetSelection(waterTargets);
    planes = new ScoutPlaneBotTargetSelection();
  }

  /** Replace one vehicle type's policy while retaining the others. */
  public StandardBotTargetSelection(
      VehicleBotTargetSelection<TorpedoBoat> torpedoBoats,
      VehicleBotTargetSelection<Submarine> submarines,
      VehicleBotTargetSelection<ScoutPlane> planes) {
    this.torpedoBoats = Objects.requireNonNull(torpedoBoats, "torpedoBoats");
    this.submarines = Objects.requireNonNull(submarines, "submarines");
    this.planes = Objects.requireNonNull(planes, "planes");
  }

  @Override
  public Vehicle select(BotTargetSituation situation, long tickId) {
    Vehicle vehicle = situation.observer();
    if (vehicle instanceof TorpedoBoat boat) return torpedoBoats.select(boat, situation, tickId);
    if (vehicle instanceof Submarine submarine)
      return submarines.select(submarine, situation, tickId);
    if (vehicle instanceof ScoutPlane plane) return planes.select(plane, situation, tickId);
    throw new IllegalArgumentException("Unsupported vehicle type: " + vehicle.getClass());
  }

  @Override
  public void observe(BotTargetSituation situation, long tickId) {
    Vehicle vehicle = situation.observer();
    if (vehicle instanceof TorpedoBoat boat) {
      torpedoBoats.observe(boat, situation, tickId);
      return;
    }
    if (vehicle instanceof Submarine submarine) {
      submarines.observe(submarine, situation, tickId);
      return;
    }
    if (vehicle instanceof ScoutPlane plane) {
      planes.observe(plane, situation, tickId);
      return;
    }
    throw new IllegalArgumentException("Unsupported vehicle type: " + vehicle.getClass());
  }

  @Override
  public void depthChargeContact(BotTargetSituation situation, Submarine target, long tickId) {
    Vehicle vehicle = situation.observer();
    if (vehicle instanceof TorpedoBoat boat) {
      torpedoBoats.depthChargeContact(boat, situation, target, tickId);
      return;
    }
    if (vehicle instanceof Submarine submarine) {
      submarines.depthChargeContact(submarine, situation, target, tickId);
      return;
    }
    if (vehicle instanceof ScoutPlane plane) {
      planes.depthChargeContact(plane, situation, target, tickId);
      return;
    }
    throw new IllegalArgumentException("Unsupported vehicle type: " + vehicle.getClass());
  }

  @Override
  public BotGoal goal(BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    Vehicle vehicle = situation.observer();
    if (vehicle instanceof TorpedoBoat boat)
      return torpedoBoats.goal(boat, situation, contact, advanceSearch);
    if (vehicle instanceof Submarine submarine)
      return submarines.goal(submarine, situation, contact, advanceSearch);
    if (vehicle instanceof ScoutPlane plane)
      return planes.goal(plane, situation, contact, advanceSearch);
    throw new IllegalArgumentException("Unsupported vehicle type: " + vehicle.getClass());
  }

  @Override
  public Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    var result = new HashMap<String, BotSubmarineHunt.Detection>();
    mergeDetections(result, torpedoBoats.submarineDetections(now));
    mergeDetections(result, submarines.submarineDetections(now));
    mergeDetections(result, planes.submarineDetections(now));
    return Map.copyOf(result);
  }

  private static void mergeDetections(
      Map<String, BotSubmarineHunt.Detection> result,
      Map<String, BotSubmarineHunt.Detection> notices) {
    notices.forEach(
        (id, notice) ->
            result.merge(
                id,
                notice,
                (a, b) ->
                    a.lifeId() > b.lifeId() || a.lifeId() == b.lifeId() && a.until() >= b.until()
                        ? a
                        : b));
  }
}
