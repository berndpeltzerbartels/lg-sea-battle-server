package one.xis.seabattle.game;

import java.util.List;
import java.util.Map;

/**
 * Observer-relative perception and persistent orders. References are the actual server vehicles.
 * Valid during selection/goal calculation only; do not move vehicles here or retain this context. A
 * remembered target reference is not permission to read its hidden current position.
 */
public final class BotTargetSituation {
  private final GameSession session;
  private final Vehicle observer;
  private final List<Vehicle> candidates;
  private final RadarService.VisibilityCache perception;
  private final Map<String, Integer> reservations;
  private boolean standardComputed;
  private Vehicle standard;

  BotTargetSituation(
      GameSession session,
      Vehicle observer,
      List<Vehicle> candidates,
      RadarService.VisibilityCache perception,
      Map<String, Integer> reservations) {
    this.session = session;
    this.observer = observer;
    this.candidates = candidates;
    this.perception = perception;
    this.reservations = reservations;
  }

  GameSession session() {
    return session;
  }

  Map<String, Integer> reservations() {
    return reservations;
  }

  public Vehicle observer() {
    return observer;
  }

  public String observerId() {
    return observer.id();
  }

  /** Existing eligible contacts, with no conversion or vehicle copies. */
  public List<Vehicle> candidates() {
    return candidates;
  }

  /** Shared active server vehicles. Use perception queries before treating enemies as contacts. */
  public List<Vehicle> vehicles() {
    return perception.vehicles();
  }

  /** Reuses the round's spatial index; no global scan and no DTO conversion. */
  public List<Vehicle> nearbyVehicles(double range) {
    return perception.candidates(observer, range);
  }

  public int candidateCount() {
    return candidates.size();
  }

  public double timeSeconds() {
    return session.calculationTime();
  }

  public Vector2 patrolCenter() {
    return session.patrolCenter;
  }

  public Vehicle currentTarget() {
    return observer instanceof SurfaceVehicle surface ? surface.botNavigation.target : null;
  }

  public String currentTargetId() {
    return currentTarget() == null ? null : currentTarget().id();
  }

  public BotSubmarineHunt.Search submarineSearch() {
    return observer instanceof SurfaceVehicle surface
        ? surface.botNavigation.submarineSearch
        : null;
  }

  public SurfaceVehicle.SubmarineAttackCourse lastSubmarineCourse() {
    return observer instanceof SurfaceVehicle surface ? surface.botSubmarineAttackCourse : null;
  }

  /** Actual visual contact, including the observer's acquired periscope tracking. */
  public boolean isVisible(Vehicle target) {
    if (observer == target
        || !"active".equals(observer.state())
        || !"active".equals(target.state())) return false;
    return !observer.isFullySubmerged()
        && (perception.isVisible(observer, target)
            || observer instanceof SurfaceVehicle
                && session.isVisiblePeriscopeRamContact(
                    observer,
                    target,
                    observer instanceof SurfaceVehicle surface
                        && surface.botNavigation.target == target
                        && surface.botNavigation.targetLife == target.lifeId()
                        && surface.botNavigation.trackingPeriscope));
  }

  public boolean hasSonarContact(Submarine target) {
    if (!"active".equals(target.state()) || target == observer || target.isOnSurface())
      return false;
    return observer instanceof Submarine
        ? session.isUnderwaterBotContact(observer, target)
        : !observer.isScoutPlane()
            && observer.position().distanceTo(target.position())
                <= GameSession.BOT_DEPTH_CHARGE_DETECTION_RADIUS
            && target.y() < -1;
  }

  public boolean isStrategicContact(Vehicle target) {
    return !observer.teamId().equals(target.teamId())
        && "active".equals(target.state())
        && !target.isScoutPlane()
        && session.isStrategicHumanContact(observer, target);
  }

  public int assignedAircraftCount(Vehicle target) {
    return reservations.getOrDefault(target.id(), 0);
  }

  /** Explicit reuse of the built-in strategy, memoized within this selection call. */
  public Vehicle standardTarget() {
    if (!standardComputed) {
      standard = new StandardBotTargetSelection().select(this, 0);
      standardComputed = true;
    }
    return standard;
  }

  public String standardTargetId() {
    Vehicle target = standardTarget();
    return target == null ? null : target.id();
  }

  Vehicle validate(Vehicle target) {
    if (target != null
        && !candidates.contains(target)
        && !(target == currentTarget()
            && lastSubmarineCourse() != null
            && timeSeconds() < lastSubmarineCourse().until()))
      throw new IllegalArgumentException(
          "Target is not an eligible candidate for " + observer.id() + ": " + target.id());
    return target;
  }
}
