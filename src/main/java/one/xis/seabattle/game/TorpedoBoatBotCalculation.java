package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

/** Torpedo-boat pursuit and attack speed; water safety is shared with submarines. */
public final class TorpedoBoatBotCalculation implements VehicleBotCalculation<TorpedoBoat> {
  private final WaterBotNavigation navigation;
  private static final WaterBotNavigation.AttackSpeed<TorpedoBoat> ATTACK_SPEED =
      TorpedoBoatBotCalculation::attackEngineOrder;

  public TorpedoBoatBotCalculation() {
    this(new WaterBotNavigation());
  }

  TorpedoBoatBotCalculation(WaterBotNavigation navigation) {
    this.navigation = navigation;
  }

  @Override
  public void calculate(TorpedoBoat boat, BotSituation situation, long tickId, BotGoal goal) {
    navigation.calculate(boat, situation, tickId, goal, null, ATTACK_SPEED);
  }

  static int attackEngineOrder(
      TorpedoBoat boat, Vehicle target, double distance, double targetBearing) {
    boolean humanTarget = target != null && target.isHumanControlled();
    if (target == null) return ENGINE_HALF; // Map-centre patrol stays relaxed.
    if (!humanTarget) {
      if (distance <= BOT_APPROACH_SLOW_RANGE) return ENGINE_HALF;
      return distance <= BotNavigation.RADAR_RING ? ENGINE_TWO_THIRDS : ENGINE_FULL;
    }
    if (target.isAtPeriscopeDepth()) {
      return ENGINE_FLANK;
    }
    // Distance alone determines when a surface boat must use its full pursuit speed.
    if (distance >= BotNavigation.RADAR_RING) {
      return ENGINE_FLANK;
    }
    double absoluteBearing = Math.abs(targetBearing);
    if (distance < BOT_RAM_RANGE) {
      return target.isSubmarine()
          ? ENGINE_FLANK
          : absoluteBearing <= Math.toRadians(30) ? ENGINE_HALF : ENGINE_FULL;
    }
    if (humanTarget && Math.abs(boat.speed()) < 0.8 && distance <= BOT_APPROACH_SLOW_RANGE) {
      return ENGINE_FULL;
    }
    if (distance < BOT_CLOSE_MANEUVER_RANGE) {
      if (absoluteBearing <= Math.toRadians(30)) {
        return ENGINE_HALF;
      }
      if (absoluteBearing <= BOT_CLOSE_FIRE_ARC) {
        return ENGINE_HALF;
      }
      return ENGINE_FULL;
    }
    if (distance > BOT_RADAR_INTERCEPT_RANGE) {
      return ENGINE_FULL;
    }
    if (distance > BOT_APPROACH_SLOW_RANGE) {
      return ENGINE_TWO_THIRDS;
    }
    return ENGINE_HALF;
  }
}
