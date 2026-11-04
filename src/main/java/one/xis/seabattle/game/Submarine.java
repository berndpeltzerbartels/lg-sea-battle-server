package one.xis.seabattle.game;

public final class Submarine extends SurfaceVehicle {
  private String depth = "surface";
  private SubmarineBot submarineBot = new SubmarineBot();

  public Submarine(String id, String team, Vector2 position, double heading, String controller) {
    super(id, team, position, heading, controller);
  }

  @Override
  public String vehicleType() {
    return "submarine";
  }

  @Override
  protected int maxRudderDegrees() {
    return 45;
  }

  @Override
  public String depthState() {
    return depth;
  }

  void copyRecordedDepth(Submarine source) {
    depth = source.depth;
  }

  SubmarineBot bot() {
    return submarineBot;
  }

  @Override
  void botDepth(String depth) {
    if (isBotControlled()) this.depth = normalizeDepth(depth);
  }

  @Override
  protected void readDepth(PlayerStateUpdate update) {
    depth = normalizeDepth(update.depthState());
  }

  @Override
  protected void readVerticalMotion(PlayerStateUpdate update) {
    if (Double.isFinite(update.y())) y = Math.min(update.y(), 0);
    else y = 0;
  }

  @Override
  protected void resetControllerState() {
    super.resetControllerState();
    submarineBot = new SubmarineBot();
  }

  @Override
  protected void updateDepth(double seconds) {
    if (isBotControlled())
      y =
          advanceDepth(
              y,
              isFullySubmerged()
                  ? SeaBattleGameConfig.SUBMARINE_DEEP_Y
                  : (-.26 + (isAtPeriscopeDepth() ? -1.6 : 0))
                      * SeaBattleGameConfig.TORPEDO_BOAT_SCALE,
              seconds);
  }

  @Override
  boolean canFire(double now) {
    return super.canFire(now)
        && (!isBotControlled()
            || (isFullySubmerged()
                ? Math.abs(y - SeaBattleGameConfig.SUBMARINE_DEEP_Y) < .1
                : y >= -1.87 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE));
  }

  @Override
  protected void resetSubtypeForRespawn() {
    super.resetSubtypeForRespawn();
    depth = "surface";
    submarineBot = new SubmarineBot();
  }

  private static String normalizeDepth(String depth) {
    return "periscope".equals(depth) ? depth : "submerged".equals(depth) ? depth : "surface";
  }

  static double advanceDepth(double current, double target, double seconds) {
    double periscope = -1.86 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    double surfaceSpeed = .28 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    double deepSpeed = Math.abs(SeaBattleGameConfig.SUBMARINE_DEEP_Y - periscope) / 6;
    // Travel-time coordinates preserve both speeds when a tick crosses periscope depth.
    double from = (current - periscope) / (current >= periscope ? surfaceSpeed : deepSpeed);
    double to = (target - periscope) / (target >= periscope ? surfaceSpeed : deepSpeed);
    double next =
        from + Math.signum(to - from) * Math.min(Math.abs(to - from), Math.max(0, seconds));
    return periscope + next * (next >= 0 ? surfaceSpeed : deepSpeed);
  }
}
