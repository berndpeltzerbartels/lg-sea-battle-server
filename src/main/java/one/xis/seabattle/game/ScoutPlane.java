package one.xis.seabattle.game;

public final class ScoutPlane extends Vehicle {
  static final double CRUISE_Y = 170, CRUISE_SPEED = 25.65;
  private static final double MIN_Y = 3 * SeaBattleGameConfig.SCOUT_PLANE_ALTITUDE_SCALE;
  private static final double MAX_Y = 200 * SeaBattleGameConfig.SCOUT_PLANE_ALTITUDE_SCALE;
  private double verticalSpeed;
  private double targetY = CRUISE_Y;

  public ScoutPlane(String id, String team, Vector2 position, double heading, String controller) {
    super(id, team, position, heading, controller);
    y = CRUISE_Y;
    speed = CRUISE_SPEED;
    engineOrder = 7;
  }

  @Override
  public String vehicleType() {
    return "scout-plane";
  }

  @Override
  protected int maxRudderDegrees() {
    return 35;
  }

  @Override
  public double verticalSpeed() {
    return verticalSpeed;
  }

  @Override
  void y(double y) {
    this.y = MathSupport.clamp(y, MIN_Y, MAX_Y);
    targetY = this.y;
  }

  @Override
  void botScoutPlaneTargetY(double y) {
    targetY = MathSupport.clamp(y, MIN_Y, MAX_Y);
  }

  @Override
  protected void readVerticalMotion(PlayerStateUpdate update) {
    y = MathSupport.clamp(update.y(), MIN_Y, MAX_Y);
    verticalSpeed = MathSupport.clamp(update.verticalSpeed(), -34, 20);
  }

  @Override
  void applyScoutPlaneWeaponState(
      Vector2 position, double y, double heading, double speed, double verticalSpeed) {
    if (!"active".equals(state)) return;
    this.position = position;
    this.y = MathSupport.clamp(y, MIN_Y, MAX_Y);
    this.heading = MathSupport.normalizeAngle(heading);
    this.speed = MathSupport.clamp(speed, -48, 48);
    this.verticalSpeed = MathSupport.clamp(verticalSpeed, -34, 20);
  }

  @Override
  void update(double seconds, NavigationService navigation, WorldMap world) {
    if (!"active".equals(state)) return;
    speed += (CRUISE_SPEED - speed) * Math.min(1, seconds * .55);
    double turn = (rudderDegrees / 35.0) * .18;
    turnVelocity += (turn - turnVelocity) * Math.min(1, seconds * 1.2);
    heading = MathSupport.normalizeAngle(heading + turnVelocity * seconds);
    position = position.add(Vector2.fromHeading(heading).scale(speed * seconds));
    y += (targetY - y) * Math.min(1, seconds * .38);
    verticalSpeed = 0;
    engineOrder = 7;
  }

  @Override
  boolean canDropBomb(double now) {
    return "active".equals(state) && now >= nextFireTime;
  }

  @Override
  protected void resetSubtypeForRespawn() {
    verticalSpeed = 0;
    targetY = CRUISE_Y;
  }
}
