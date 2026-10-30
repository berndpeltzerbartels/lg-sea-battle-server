package one.xis.seabattle.game;

import java.util.List;

/** Live round context. Use only during calculate(); snapshots returned by ships() are immutable.
 * ships() includes all participants, not just detected enemies. Experimental algorithms must
 * apply their own detection rules. The production algorithm retains its existing radar rules.
 */
public final class BotSituation {
    private final GameSession session;
    private final RadarService radar;
    private final NavigationService navigation;
    private final WorldMap world;
    private final GameSession.BotRound round;
    private List<ShipSnapshot> snapshots;
    private java.util.Map<String, Ship> bots;
    private boolean standardCalled;

    BotSituation(GameSession session, RadarService radar, NavigationService navigation, WorldMap world, GameSession.BotRound round) {
        this.session = session;
        this.radar = radar;
        this.navigation = navigation;
        this.world = world;
        this.round = round;
    }

    public double timeSeconds() { return session.calculationTime(); }

    /** Captured lazily once, before any subsequent commands issued by this implementation. */
    public List<ShipSnapshot> ships() {
        if (snapshots == null) snapshots = session.calculationShips().stream().map(Ship::snapshot).toList();
        return snapshots;
    }

    public boolean isLand(double x, double z) {
        return LandGeometry.isBlocked(new Vector2(x, z), world);
    }

    /** Real ship footprint, heading in radians (0 = +z). */
    public boolean isHullBlocked(double x, double z, double heading) {
        return navigation.isShipBlocked(new Vector2(x, z), heading, world);
    }

    /** Engine 0..8 (2 = stop), rudder degrees; existing vessel limits are enforced. */
    public void steer(String shipId, int engineOrder, int rudderDegrees) {
        bot(shipId).applyCommand(engineOrder, rudderDegrees);
    }

    public void dive(String shipId, String depth) {
        if (!List.of("surface", "periscope", "submerged").contains(depth))
            throw new IllegalArgumentException("Unknown depth: " + depth);
        bot(shipId).botDepth(depth);
    }

    /** Run existing movement/combat controls once, using this round’s already prepared goals. */
    public void runStandard() { runStandard(round.goals()); }

    /** Existing movement/combat controls using the already selected navigation destinations. */
    public void runStandard(java.util.Map<String, BotGoal> goals) {
        if (standardCalled) throw new IllegalStateException("Production calculation already ran this round");
        standardCalled = true;
        session.calculateStandardBots(radar, navigation, world, round, goals);
    }

    private Ship bot(String id) {
        if (bots == null) {
            bots = new java.util.HashMap<>();
            for (Ship ship : session.calculationShips())
                if (ship.isBotControlled() && "active".equals(ship.state())) bots.put(ship.id(), ship);
        }
        Ship ship = bots.get(id);
        if (ship == null) throw new IllegalArgumentException("No active bot: " + id);
        return ship;
    }
}
