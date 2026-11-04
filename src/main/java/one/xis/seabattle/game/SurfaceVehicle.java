package one.xis.seabattle.game;

/** State shared by torpedo boats and submarines, never allocated for aircraft. */
public abstract sealed class SurfaceVehicle extends Vehicle permits TorpedoBoat, Submarine {
  private double flakYaw = Math.PI, flakPitch;
  private double backoffUntil = Double.NEGATIVE_INFINITY;
  private Vehicle backoffContact;
  private long backoffContactLife;
  private Vector2 backoffStart;
  double botEscapeHeading, botEscapeUntil, botReverseUntil, botForwardEscapeUntil;

  public record SubmarineAttackCourse(
      String targetId, Vector2 position, double heading, double until) {}

  SubmarineAttackCourse botSubmarineAttackCourse;
  BotNavigation botNavigation;
  Vector2 motionStartPosition;
  double motionStartHeading;

  protected SurfaceVehicle(
      String id, String team, Vector2 position, double heading, String controller) {
    super(id, team, position, heading, controller);
    botNavigation = new BotNavigation(id);
    motionStartPosition = position;
    motionStartHeading = heading;
  }

  @Override
  protected void resetControllerState() {
    botNavigation = new BotNavigation(id());
    botSubmarineAttackCourse = null;
    clearImpactState();
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
    motionStartPosition = position;
    motionStartHeading = heading;
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

  boolean hasGlancingRamBackoff(double now) {
    if (backoffContact == null) return false;
    if (!"active".equals(backoffContact.state())
        || backoffContact.lifeId() != backoffContactLife
        || position.distanceTo(backoffContact.position()) >= GameRules.RAM_COLLISION_BROAD_PHASE_RADIUS) {
      clearImpactState();
      return false;
    }
    return "active".equals(state) && now < backoffUntil && position.distanceTo(backoffStart) < 1.5;
  }

  @Override
  boolean applyGlancingRamBackoff(double now) {
    if (!hasGlancingRamBackoff(now)) return false;
    applyCommand(GameRules.ENGINE_ASTERN, backoffRudder());
    return true;
  }

  void cancelGlancingRamBackoff() {
    backoffUntil = Double.NEGATIVE_INFINITY;
    // Keep the contact latch: an obstructed stern must not start another attempt next tick.
  }

  @Override
  void backOffAfterGlancingRam(double now, double duration, Vehicle other) {
    if (!"active".equals(state)) return;
    if (backoffContact != null
        && "active".equals(backoffContact.state())
        && backoffContact.lifeId() == backoffContactLife
        && position.distanceTo(backoffContact.position()) < GameRules.RAM_COLLISION_BROAD_PHASE_RADIUS) return;
    backoffContact = other;
    backoffContactLife = other.lifeId();
    backoffStart = position;
    backoffUntil = now + duration;
    speed = Math.min(0, speed * .15);
    turnVelocity *= .25;
    // The next navigation round checks land and friendly traffic behind before reversing.
    engineOrder = GameRules.ENGINE_STOP;
    rudderDegrees = 0;
  }

  private int backoffRudder() {
    // Reverse rudder supports the normal port-side passing manoeuvre.
    return maxRudderDegrees();
  }

  @Override
  protected void clearImpactState() {
    backoffUntil = Double.NEGATIVE_INFINITY;
    backoffContact = null;
    backoffStart = null;
  }

  @Override
  protected void resetSubtypeForRespawn() {
    botEscapeUntil = 0;
    botReverseUntil = 0;
    botForwardEscapeUntil = 0;
    botSubmarineAttackCourse = null;
    botNavigation = new BotNavigation(id());
    motionStartPosition = position;
    motionStartHeading = heading;
    clearImpactState();
  }
}
