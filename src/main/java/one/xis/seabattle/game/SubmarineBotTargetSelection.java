package one.xis.seabattle.game;

import java.util.Map;
import java.util.Objects;

/** Standard submarine contacts and reports from an exposed periscope. */
public final class SubmarineBotTargetSelection implements VehicleBotTargetSelection<Submarine> {
  private final WaterBotTargetSelection waterTargets;

  public SubmarineBotTargetSelection() {
    this(new WaterBotTargetSelection());
  }

  SubmarineBotTargetSelection(WaterBotTargetSelection waterTargets) {
    this.waterTargets = Objects.requireNonNull(waterTargets);
  }

  @Override
  public Vehicle select(Submarine submarine, BotTargetSituation situation, long tickId) {
    return waterTargets.select(submarine, situation, tickId);
  }

  @Override
  public void observe(Submarine submarine, BotTargetSituation situation, long tickId) {
    waterTargets.observe(situation, tickId);
  }

  @Override
  public Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    return waterTargets.submarineDetections(now);
  }

  @Override
  public BotGoal goal(
      Submarine submarine, BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    return contact != null
        ? BotGoal.enemy(contact)
        : waterTargets.fallbackGoal(submarine, situation);
  }
}
