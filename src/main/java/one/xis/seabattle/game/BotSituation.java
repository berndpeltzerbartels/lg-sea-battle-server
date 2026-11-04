package one.xis.seabattle.game;

import java.util.List;

/**
 * Direct server model for one movement round. Never builds frontend snapshots. Each bot owns its
 * command/state; target selection is already complete for all vehicles. Do not retain this round
 * context across calls.
 */
public final class BotSituation {
  private final GameSession session;
  private final RadarService radar;
  private final NavigationService navigation;
  private final WorldMap world;
  private final GameSession.BotRound round;
  private boolean standardCalled;
  private List<BotWreckObstacle> wreckObstacles;

  BotSituation(
      GameSession session,
      RadarService radar,
      NavigationService navigation,
      WorldMap world,
      GameSession.BotRound round) {
    this.session = session;
    this.radar = radar;
    this.navigation = navigation;
    this.world = world;
    this.round = round;
  }

  GameSession session() {
    return session;
  }

  RadarService radar() {
    return radar;
  }

  NavigationService navigation() {
    return navigation;
  }

  GameSession.BotRound round() {
    return round;
  }

  List<BotWreckObstacle> wreckObstacles() {
    if (wreckObstacles == null) wreckObstacles = session.botWreckObstacles();
    return wreckObstacles;
  }

  public WorldMap world() {
    return world;
  }

  public double timeSeconds() {
    return session.calculationTime();
  }

  /** Actual active vehicles, including human participants. No copies or rounding. */
  public List<Vehicle> vehicles() {
    return round.activeVehicles();
  }

  /** Compatibility name for existing experiments; includes aircraft too. */
  public List<Vehicle> ships() {
    return round.activeVehicles();
  }

  public boolean isLand(double x, double z) {
    return LandGeometry.isBlocked(new Vector2(x, z), world);
  }

  public boolean isHullBlocked(double x, double z, double heading) {
    return navigation.isShipBlocked(new Vector2(x, z), heading, world);
  }

  public BotTargetSituation perception(Vehicle observer) {
    return new BotTargetSituation(session, observer, List.of(), round.cache(), java.util.Map.of());
  }

  public void steer(Vehicle bot, int engineOrder, int rudderDegrees) {
    requireBot(bot);
    bot.applyCommand(engineOrder, rudderDegrees);
  }

  public void steer(String id, int engineOrder, int rudderDegrees) {
    steer(bot(id), engineOrder, rudderDegrees);
  }

  public void dive(Submarine bot, String depth) {
    requireBot(bot);
    if (!List.of("surface", "periscope", "submerged").contains(depth))
      throw new IllegalArgumentException("Unknown depth: " + depth);
    bot.botDepth(depth);
  }

  public void dive(String id, String depth) {
    if (!(bot(id) instanceof Submarine sub))
      throw new IllegalArgumentException("Not a submarine: " + id);
    dive(sub, depth);
  }

  public void runStandard() {
    runStandard(round.goals());
  }

  public void runStandard(java.util.Map<String, BotGoal> goals) {
    if (standardCalled)
      throw new IllegalStateException("Production calculation already ran this round");
    standardCalled = true;
    new StandardBotCalculation()
        .calculate(this, (long) Math.floor(timeSeconds() * 10 + 1e-6), goals);
  }

  private void requireBot(Vehicle vehicle) {
    if (!vehicle.isBotControlled()
        || !"active".equals(vehicle.state())
        || !ships().contains(vehicle))
      throw new IllegalArgumentException("No active bot: " + vehicle.id());
  }

  private Vehicle bot(String id) {
    return ships().stream()
        .filter(vehicle -> vehicle.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No active vehicle: " + id));
  }
}
