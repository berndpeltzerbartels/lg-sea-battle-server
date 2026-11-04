package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.List;

/** Submarine dive/retreat tactics and attack speed, with shared water navigation. */
public final class SubmarineBotCalculation implements VehicleBotCalculation<Submarine> {
  private final WaterBotNavigation navigation;
  private static final WaterBotNavigation.AttackSpeed<Submarine> ATTACK_SPEED =
      SubmarineBotCalculation::attackEngineOrder;

  public SubmarineBotCalculation() {
    this(new WaterBotNavigation());
  }

  SubmarineBotCalculation(WaterBotNavigation navigation) {
    this.navigation = navigation;
  }

  @Override
  public void calculate(Submarine submarine, BotSituation situation, long tickId, BotGoal goal) {
    GameSession session = situation.session();
    RadarService.VisibilityCache perception = situation.round().cache();
    List<Vehicle> contacts =
        submarine.isFullySubmerged()
            ? perception.candidates(submarine, SubmarineBot.UNDERWATER_RANGE)
            : perception.candidates(submarine, RadarService.RADAR_RANGE).stream()
                .filter(
                    target ->
                        session.isUnderwaterBotContact(submarine, target)
                            || perception.isVisible(submarine, target, RadarService.RADAR_RANGE))
                .toList();
    boolean ascentShot =
        submarine.isFullySubmerged()
            && contacts.stream()
                .filter(target -> !target.teamId().equals(submarine.teamId()))
                .filter(
                    target ->
                        "active".equals(target.state())
                            && target.isOnSurface()
                            && !target.isScoutPlane())
                .anyMatch(target -> session.submarineHasAscentShot(submarine, target));
    Vector2 retreatFrom =
        submarine.bot().update(submarine, contacts, situation.timeSeconds(), ascentShot);
    navigation.calculate(submarine, situation, tickId, goal, retreatFrom, ATTACK_SPEED);
  }

  static int attackEngineOrder(
      Submarine submarine, Vehicle target, double distance, double targetBearing) {
    boolean humanTarget = target != null && target.isHumanControlled();
    if (target != null && !submarine.isOnSurface()) {
      return submarine.bot().attackEngineOrder(submarine, target, distance, targetBearing);
    }
    if (target == null) return ENGINE_HALF; // Map-centre patrol stays relaxed.
    if (!humanTarget) {
      if (distance <= BOT_APPROACH_SLOW_RANGE) return ENGINE_HALF;
      return distance <= BotNavigation.RADAR_RING ? ENGINE_TWO_THIRDS : ENGINE_FULL;
    }
    if (target.isAtPeriscopeDepth()) {
      return ENGINE_FULL;
    }
    double absoluteBearing = Math.abs(targetBearing);
    if (distance < BOT_RAM_RANGE) {
      return absoluteBearing <= Math.toRadians(30) ? ENGINE_HALF : ENGINE_FULL;
    }
    if (humanTarget && Math.abs(submarine.speed()) < 0.8 && distance <= BOT_APPROACH_SLOW_RANGE) {
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
