package one.xis.seabattle.game;

/** Common identity, motion and life cycle. Concrete types own their specialist state. */
public abstract sealed class Vehicle permits SurfaceVehicle, ScoutPlane {
  private final String id;
  private final String teamId;
  protected Vector2 position;
  protected double y, heading, speed, turnVelocity;
  protected int engineOrder = 2, rudderDegrees;
  protected String controlledBy;
  protected String state = "active";
  private int torpedoesRemaining = 12;
  protected double nextFireTime;
  private double respawnAtSeconds = Double.POSITIVE_INFINITY;
  private long lifeId;

  protected Vehicle(String id, String teamId, Vector2 position, double heading, String controller) {
    this.id = id;
    this.teamId = teamId;
    this.position = position;
    this.heading = heading;
    this.controlledBy = controller;
  }

  static Vehicle create(
      String type, String id, String team, Vector2 position, double heading, String controller) {
    return switch (type == null ? "torpedo-boat" : type) {
      case "submarine" -> new Submarine(id, team, position, heading, controller);
      case "scout-plane" -> new ScoutPlane(id, team, position, heading, controller);
      default -> new TorpedoBoat(id, team, position, heading, controller);
    };
  }

  /** Fleet replaces the instance; old target references are invalidated explicitly. */
  final Vehicle convertedTo(String type) {
    if (vehicleType().equals(type) || type == null) return this;
    Vehicle next = create(type, id, teamId, position, heading, controlledBy);
    next.speed = next.isScoutPlane() ? Math.max(speed, ScoutPlane.CRUISE_SPEED) : speed;
    next.engineOrder = next.isScoutPlane() ? 7 : engineOrder;
    next.rudderDegrees =
        MathSupport.clamp(rudderDegrees, -next.maxRudderDegrees(), next.maxRudderDegrees());
    next.turnVelocity = turnVelocity;
    next.state = state;
    next.lifeId = lifeId;
    next.nextFireTime = nextFireTime;
    next.respawnAtSeconds = respawnAtSeconds;
    next.torpedoesRemaining = torpedoesRemaining;
    if (next instanceof Submarine && this instanceof SurfaceVehicle) next.y = Math.min(y, 0);
    if (next instanceof SurfaceVehicle surface && this instanceof SurfaceVehicle old) {
      surface.flakAim(old.flakYawValue(), old.flakPitchValue());
    }
    return next;
  }

  /** Preserve an actual typed vehicle once at destruction; never maps through a wire DTO. */
  final Vehicle copyForWreck() {
    Vehicle copy = create(vehicleType(), id, teamId, position, heading, controlledBy);
    copy.y = y;
    copy.speed = speed;
    copy.turnVelocity = turnVelocity;
    copy.engineOrder = engineOrder;
    copy.rudderDegrees = rudderDegrees;
    copy.state = state;
    copy.lifeId = lifeId;
    copy.flakAim(flakYawValue(), flakPitchValue());
    copy.cannonAim(cannonYawValue(), cannonPitchValue());
    if (copy instanceof Submarine sub) sub.copyRecordedDepth((Submarine) this);
    return copy;
  }

  final void retired() {
    state = "replaced";
  }

  public final String id() {
    return id;
  }

  public final String teamId() {
    return teamId;
  }

  public final String state() {
    return state;
  }

  public final String controlledBy() {
    return controlledBy;
  }

  public abstract String vehicleType();

  public final boolean isScoutPlane() {
    return this instanceof ScoutPlane;
  }

  public final boolean isSubmarine() {
    return this instanceof Submarine;
  }

  public final boolean isFullySubmerged() {
    return isSubmarine() && "submerged".equals(depthState());
  }

  public final boolean isAtPeriscopeDepth() {
    return isSubmarine() && "periscope".equals(depthState());
  }

  public final boolean isOnSurface() {
    return !isSubmarine() || "surface".equals(depthState());
  }

  public final boolean isHumanControlled() {
    return controlledBy != null
        && !controlledBy.isBlank()
        && !"bot".equals(controlledBy)
        && !"scenario".equals(controlledBy);
  }

  public final boolean isBotControlled() {
    return "bot".equals(controlledBy);
  }

  public final boolean isServerSimulated() {
    return isBotControlled() || "scenario".equals(controlledBy);
  }

  final void controlledBy(String controller) {
    if (!java.util.Objects.equals(controlledBy, controller)) resetControllerState();
    controlledBy = controller;
  }

  protected void resetControllerState() {}

  final SurfaceVehicle surface() {
    if (this instanceof SurfaceVehicle surface) return surface;
    throw new IllegalStateException("Aircraft has no surface navigation state: " + id);
  }

  final SubmarineBot submarineBot() {
    return ((Submarine) this).bot();
  }

  void botDepth(String depth) {}

  public final Vector2 position() {
    return position;
  }

  public final long lifeId() {
    return lifeId;
  }

  public final double heading() {
    return heading;
  }

  public final double speed() {
    return speed;
  }

  public final double y() {
    return y;
  }

  public final int engineOrder() {
    return engineOrder;
  }

  public final int rudderDegrees() {
    return rudderDegrees;
  }

  public final double turnVelocity() {
    return turnVelocity;
  }

  public double verticalSpeed() {
    return 0;
  }

  public String depthState() {
    return "surface";
  }

  void flakAim(double yaw, double pitch) {}

  void cannonAim(double yaw, double pitch) {}

  protected double flakYawValue() {
    return Math.PI;
  }

  protected double flakPitchValue() {
    return 0;
  }

  protected double cannonYawValue() {
    return 0;
  }

  protected double cannonPitchValue() {
    return 0;
  }

  final void nextFireTime(double time) {
    nextFireTime = time;
  }

  void y(double y) {
    this.y = 0;
  }

  void botScoutPlaneTargetY(double y) {}

  protected abstract int maxRudderDegrees();

  final void applyCommand(int engine, int rudder) {
    if (!"active".equals(state)) return;
    engineOrder = MathSupport.clamp(engine, 0, 8);
    rudderDegrees = MathSupport.clamp(rudder, -maxRudderDegrees(), maxRudderDegrees());
  }

  boolean applyGlancingRamBackoff(double now) {
    return false;
  }

  void backOffAfterGlancingRam(double now, double duration, Vector2 target) {}

  protected void readDepth(PlayerStateUpdate update) {}

  protected void readVerticalMotion(PlayerStateUpdate update) {
    y = 0;
  }

  final void applyPlayerState(
      PlayerStateUpdate update, NavigationService navigation, WorldMap world) {
    if (!"active".equals(state)) return;
    if (!vehicleType().equals(update.vehicleType()))
      throw new IllegalArgumentException("Replace the vehicle before changing its type: " + id);
    readDepth(update);
    Vector2 requested = new Vector2(update.x(), update.z());
    boolean implausible = position.distanceTo(requested) > 90;
    boolean blocked =
        !isScoutPlane()
            && navigation.isShipBlocked(requested, update.heading(), world)
            && navigation.isShipMovementBlocked(requested, update.heading(), update.speed(), world);
    if ((!update.debugTeleport() && implausible) || blocked) {
      applyCommand(update.engineOrder(), update.rudderDegrees());
      return;
    }
    position = requested;
    readVerticalMotion(update);
    heading = MathSupport.normalizeAngle(update.heading());
    speed = MathSupport.clamp(update.speed(), -48, 48);
    turnVelocity = MathSupport.clamp(update.turnVelocity(), -1.2, 1.2);
    applyCommand(update.engineOrder(), update.rudderDegrees());
    if (finite(update.flakYaw()) && finite(update.flakPitch()))
      flakAim(update.flakYaw(), update.flakPitch());
    if (finite(update.cannonYaw()) && finite(update.cannonPitch()))
      cannonAim(update.cannonYaw(), update.cannonPitch());
  }

  private static boolean finite(Double d) {
    return d != null && Double.isFinite(d);
  }

  void applyScoutPlaneWeaponState(
      Vector2 position, double y, double heading, double speed, double verticalSpeed) {}

  abstract void update(double seconds, NavigationService navigation, WorldMap world);

  boolean canFire(double now) {
    return false;
  }

  boolean canDropBomb(double now) {
    return false;
  }

  final void markFired(double now, double cooldown) {
    nextFireTime = now + cooldown;
    torpedoesRemaining = 12;
  }

  final void stopAfterRamImpact() {
    if (!"active".equals(state)) return;
    speed = 0;
    turnVelocity = 0;
    engineOrder = 2;
    rudderDegrees = 0;
  }

  final void glanceOff(double headingImpulse, double speedFactor) {
    if (!"active".equals(state)) return;
    heading = MathSupport.normalizeAngle(heading + headingImpulse);
    speed *= MathSupport.clamp(speedFactor, 0, 1);
    turnVelocity *= .25;
    engineOrder = Math.min(engineOrder, 4);
    rudderDegrees = 0;
  }

  protected void clearImpactState() {}

  final boolean sink(double respawnAt) {
    if (!"active".equals(state)) return false;
    state = "sunk";
    speed = 0;
    turnVelocity = 0;
    rudderDegrees = 0;
    engineOrder = 2;
    controlledBy = "bot";
    respawnAtSeconds = respawnAt;
    clearImpactState();
    return true;
  }

  final boolean isReadyToRespawn(double now) {
    return "sunk".equals(state) && now >= respawnAtSeconds;
  }

  final boolean isVisibleInSnapshot() {
    return "active".equals(state) || "sunk".equals(state);
  }

  protected void resetSubtypeForRespawn() {}

  final void respawn(Vector2 position, double heading, double now) {
    lifeId++;
    this.position = position;
    this.heading = MathSupport.normalizeAngle(heading);
    speed = isScoutPlane() ? ScoutPlane.CRUISE_SPEED : 0;
    engineOrder = isScoutPlane() ? 7 : 2;
    turnVelocity = 0;
    rudderDegrees = 0;
    controlledBy = "bot";
    y = isScoutPlane() ? ScoutPlane.CRUISE_Y : 0;
    state = "active";
    torpedoesRemaining = 12;
    nextFireTime = now + 3;
    respawnAtSeconds = Double.POSITIVE_INFINITY;
    resetSubtypeForRespawn();
  }

  final VehicleSnapshot snapshot() {
    return new VehicleSnapshot(
        id,
        teamId,
        MathSupport.round(position.x()),
        MathSupport.round(position.z()),
        MathSupport.round(heading),
        MathSupport.round(speed),
        MathSupport.round(turnVelocity),
        rudderDegrees,
        engineOrder,
        state,
        controlledBy,
        torpedoesRemaining,
        vehicleType(),
        MathSupport.round(y),
        MathSupport.round(flakYawValue()),
        MathSupport.round(flakPitchValue()),
        MathSupport.round(cannonYawValue()),
        MathSupport.round(cannonPitchValue()),
        depthState(),
        lifeId);
  }
}
