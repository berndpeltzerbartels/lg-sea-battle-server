package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.List;

/** Owns submarine decisions: attack spacing, diving and threat escape. Navigation executes them. */
public final class SubmarineBotCalculation implements VehicleBotCalculation<Submarine> {
  private static final double DIVE_RANGE = 500;
  private static final double DEEP_RANGE = 130;
  private static final double SAFE_RANGE = 220;
  private static final double SHOT_ASCENT_RANGE = 180;
  private static final double SURFACE_RANGE = 650;
  static final double ATTACK_MIN_RANGE = 250;
  static final double ATTACK_MAX_RANGE = 400;
  private static final double AIM_TOLERANCE = Math.toRadians(.15);

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
            ? perception.candidates(submarine, BOT_SUBMARINE_UNDERWATER_RANGE)
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
    Vector2 escapeFromThreat =
        updateDiveAndEscape(submarine, contacts, situation.timeSeconds(), ascentShot);
    navigation.calculate(submarine, situation, tickId, goal, escapeFromThreat, ATTACK_SPEED);
  }

  static int attackEngineOrder(
      Submarine submarine, Vehicle target, double distance, double targetBearing) {
    boolean humanTarget = target != null && target.isHumanControlled();
    if (target != null && submarine.isAtPeriscopeDepth()) {
      return holdPeriscopeAttackDistance(submarine, target, distance, targetBearing);
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

  /** Keep a firing band, not a single distance. Only a too-close target calls for reverse. */
  static int holdPeriscopeAttackDistance(
      Submarine submarine, Vehicle target, double distance, double targetBearing) {
    double bearing = Math.abs(targetBearing);
    // If the target is behind us, turn ahead; reversing would move closer to it.
    if (bearing > Math.PI / 3) return ENGINE_HALF;
    double radialTargetSpeed = target.speed()
        * Math.cos(target.heading() - submarine.heading() - targetBearing);
    if (distance < ATTACK_MIN_RANGE) {
      double retreatSpeed = -Math.max(2.2,
          (ATTACK_MIN_RANGE - distance) * .04 + Math.max(0, -radialTargetSpeed));
      return closestEngineOrder(retreatSpeed, ENGINE_FULL_ASTERN, ENGINE_ASTERN);
    }
    BotTorpedoAim aim = BotTorpedoAim.forTarget(submarine, target);
    Vector2 toTarget = target.position().subtract(submarine.position());
    double lead = aim == null ? 0 : MathSupport.normalizeAngle(
        aim.heading() - Math.atan2(toTarget.x(), toTarget.z()));
    double aimingError = Math.abs(MathSupport.normalizeAngle(targetBearing + lead));
    if (aimingError > AIM_TOLERANCE || aim != null && Math.abs(aim.angularVelocity()) > .001)
      return ENGINE_ONE_THIRD;
    if (distance <= ATTACK_MAX_RANGE) return ENGINE_STOP;
    double approachSpeed = Math.max(EngineOrders.speedFor(ENGINE_ONE_THIRD),
        Math.max(0, radialTargetSpeed) + (distance - ATTACK_MAX_RANGE) * .04);
    return closestEngineOrder(approachSpeed, ENGINE_ONE_THIRD, ENGINE_FULL);
  }

  private static int closestEngineOrder(double speed, int first, int last) {
    int best = first;
    double bestError = Double.POSITIVE_INFINITY;
    for (int order = first; order <= last; order++) {
      double error = Math.abs(EngineOrders.speedFor(order) - speed);
      if (error < bestError) { best = order; bestError = error; }
    }
    return best;
  }

  /** Dive/escape: returns a threat position to flee from with forward power, not reverse. */
  static Vector2 updateDiveAndEscape(Submarine submarine, List<Vehicle> contacts, double now) {
    return updateDiveAndEscape(submarine, contacts, now, false);
  }

  static Vector2 updateDiveAndEscape(
      Submarine submarine, List<Vehicle> contacts, double now, boolean hasShotOpportunity) {
    SubmarineBotState state = submarine.botState();
    if (!submarine.isFullySubmerged()) {
      Vehicle aircraft =
          contacts.stream()
              .filter(other -> other.isScoutPlane() && "active".equals(other.state()))
              .filter(other -> !other.teamId().equals(submarine.teamId()))
              .min(
                  java.util.Comparator.comparingDouble(
                      other -> submarine.position().distanceTo(other.position())))
              .orElse(null);
      state.lastAircraftPosition = aircraft == null ? null : aircraft.position();
      state.aircraftMemoryUntil = now + 10;
    }
    Vehicle nearest =
        contacts.stream()
            .filter(other -> !other.teamId().equals(submarine.teamId()))
            .filter(other -> "active".equals(other.state()) && !other.isScoutPlane())
            .filter(
                other ->
                    !submarine.isFullySubmerged()
                        || submarine.position().distanceTo(other.position())
                            <= BOT_SUBMARINE_UNDERWATER_RANGE)
            .min(
                java.util.Comparator.comparingDouble(
                    other -> submarine.position().distanceTo(other.position())))
            .orElse(null);
    double distance =
        nearest == null
            ? Double.POSITIVE_INFINITY
            : submarine.position().distanceTo(nearest.position());
    Vector2 threatPosition = nearest == null ? null : nearest.position();
    boolean aircraftThreat = false;
    if (state.lastAircraftPosition != null && now < state.aircraftMemoryUntil) {
      double aircraftDistance = submarine.position().distanceTo(state.lastAircraftPosition);
      if (aircraftDistance < distance) {
        distance = aircraftDistance;
        threatPosition = state.lastAircraftPosition;
        aircraftThreat = true;
      }
    }

    // Follow deep contacts at their depth; escape only when they are dangerously close.
    if (!aircraftThreat
        && nearest != null
        && nearest.isFullySubmerged()
        && distance <= BOT_SUBMARINE_UNDERWATER_RANGE) {
      state.divePhase = "submerged";
      submarine.botDepth(state.divePhase);
      return distance < DEEP_RANGE ? threatPosition : null;
    }

    if ("submerged".equals(state.divePhase)) {
      if (distance < SAFE_RANGE
          && !(hasShotOpportunity && !aircraftThreat && distance >= SHOT_ASCENT_RANGE)) {
        submarine.botDepth(state.divePhase);
        return threatPosition;
      }
      if (threatPosition == null) surface(state);
      else state.divePhase = "periscope";
      submarine.botDepth(state.divePhase);
      return null; // Reacquire contacts on the next simulation tick.
    }
    if (distance <= DEEP_RANGE) {
      state.divePhase = "submerged";
    } else if (distance <= DIVE_RANGE) {
      state.divePhase = "periscope";
    } else if (distance > SURFACE_RANGE) {
      surface(state);
    }
    submarine.botDepth(state.divePhase);
    return "submerged".equals(state.divePhase) ? threatPosition : null;
  }

  private static void surface(SubmarineBotState state) {
    state.divePhase = "surface";
  }
}
