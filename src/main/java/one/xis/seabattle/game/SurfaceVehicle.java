package one.xis.seabattle.game;

/** State shared by torpedo boats and submarines, never allocated for aircraft. */
public abstract sealed class SurfaceVehicle extends Vehicle permits TorpedoBoat, Submarine {
  private double flakYaw = Math.PI, flakPitch;
  private double backoffUntil = Double.NEGATIVE_INFINITY;
  private Vector2 backoffTarget;
  double botEscapeHeading, botEscapeUntil, botReverseUntil, botForwardEscapeUntil;

  public record SubmarineAttackCourse(
      String targetId, Vector2 position, double heading, double until) {}

  SubmarineAttackCourse botSubmarineAttackCourse;
  BotNavigation botNavigation;

  protected SurfaceVehicle(
      String id, String team, Vector2 position, double heading, String controller) {
    super(id, team, position, heading, controller);
    botNavigation = new BotNavigation(id);
  }

  @Override
  protected void resetControllerState() {
    botNavigation = new BotNavigation(id());
    botSubmarineAttackCourse = null;
  }

  @Override
  void flakAim(double yaw, double pitch) {
    flakYaw = MathSupport.normalizeAngle(yaw);
    flakPitch = pitch;
  }

  @Override
  protected double flakYawValue() {
    return flakYaw;
  }

  @Override
  protected double flakPitchValue() {
    return flakPitch;
  }

  protected void updateDepth(double seconds) {}

  @Override
  void update(double seconds, NavigationService navigation, WorldMap world) {
    if (!"active".equals(state)) return;
    updateDepth(seconds);
    Vector2 previous = position;
    double previousSpeed = speed;
    double target = EngineOrders.speedFor(engineOrder);
    if (isFullySubmerged()) target = MathSupport.clamp(target, -6, 6);
    speed +=
        (target - speed) * Math.min(1, seconds * (Math.abs(target) > Math.abs(speed) ? .45 : .42));
    double targetTurn =
        (rudderDegrees / 35.0)
            * (speed >= 0 ? .24 : -.16)
            * MathSupport.clamp(Math.abs(speed) / 4.2, 0, 1);
    turnVelocity += (targetTurn - turnVelocity) * Math.min(1, seconds * 2);
    heading = MathSupport.normalizeAngle(heading + turnVelocity * seconds);
    position = position.add(Vector2.fromHeading(heading).scale(speed * seconds));
    if (navigation.isShipBlocked(position, heading, world)
        && navigation.isShipMovementBlocked(position, heading, speed, world)) {
      position = previous;
      speed = Math.min(0, previousSpeed * .15);
      turnVelocity *= .35;
    }
  }

  @Override
  boolean canFire(double now) {
    return "active".equals(state) && now >= nextFireTime;
  }

  @Override
  boolean applyGlancingRamBackoff(double now) {
    if (!"active".equals(state) || now >= backoffUntil) {
      backoffTarget = null;
      return false;
    }
    applyCommand(0, backoffRudder());
    return true;
  }

  @Override
  void backOffAfterGlancingRam(double now, double duration, Vector2 target) {
    if (!"active".equals(state)) return;
    boolean backing = now < backoffUntil;
    backoffUntil = Math.max(backoffUntil, now + duration);
    backoffTarget = target;
    if (!backing) {
      speed = Math.min(0, speed * .15);
      turnVelocity *= .25;
    }
    engineOrder = 0;
    rudderDegrees = backoffRudder();
  }

  private int backoffRudder() {
    if (backoffTarget == null) return 0;
    double bearing = Math.atan2(backoffTarget.x() - position.x(), backoffTarget.z() - position.z());
    return (int)
        Math.round(
            MathSupport.clamp(-MathSupport.normalizeAngle(bearing - heading) / .58, -1, 1)
                * maxRudderDegrees());
  }

  @Override
  protected void clearImpactState() {
    backoffUntil = Double.NEGATIVE_INFINITY;
  }

  @Override
  protected void resetSubtypeForRespawn() {
    botEscapeUntil = 0;
    botReverseUntil = 0;
    botForwardEscapeUntil = 0;
    botSubmarineAttackCourse = null;
    botNavigation = new BotNavigation(id());
    backoffUntil = Double.NEGATIVE_INFINITY;
    backoffTarget = null;
  }
}
