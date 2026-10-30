package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** In-memory test API. Does not start HTTP, a game loop, or a browser. */
public final class BotScenario {
    public static final double TICK_SECONDS = .1;
    private final String script;
    private final ScenarioDefinition definition;
    private final int threads;
    private final boolean parallelNavigation;
    private final Supplier<BotTargetSelection> targetSelection;

    private BotScenario(String script, int threads, boolean parallelNavigation, Supplier<BotTargetSelection> targetSelection) {
        this.targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        this.script = script;
        this.definition = ScenarioScriptParser.parseDefinition(script);
        if (!definition.actions().isEmpty())
            throw new IllegalArgumentException("Bot lab scenarios use initial objects, not scripted weapon actions");
        if (threads != 1 && threads != 2) throw new IllegalArgumentException("threads must be 1 or 2");
        this.threads = threads;
        this.parallelNavigation = parallelNavigation;
    }

    public static BotScenario parse(String script) { return new BotScenario(script, 1, false, BotTargetSelection::standard); }

    public BotScenario withThreads(int threads, boolean parallelNavigation) {
        return new BotScenario(script, threads, parallelNavigation, targetSelection);
    }

    /** Independent of the navigation/calculation implementation; recreated for each run. */
    public BotScenario withTargetSelection(Supplier<BotTargetSelection> implementation) {
        return new BotScenario(script, threads, parallelNavigation, implementation);
    }

    public BotTestRun run(Supplier<BotCalculation> implementation, int ticks) {
        return execute(implementation, ticks, true);
    }

    /** Warmups and measured runs each start from a new game AND a fresh algorithm instance.
     * Measures target select(), movement calculate() and the complete simulation step separately; excludes snapshots and output.
     */
    public List<BotTestRun.Timing> benchmark(Supplier<BotCalculation> implementation, int ticks,
                                           int warmups, int rounds) {
        if (warmups < 0 || rounds < 1) throw new IllegalArgumentException("Invalid benchmark rounds");
        for (int i = 0; i < warmups; i++) execute(implementation, ticks, false);
        List<BotTestRun.Timing> results = new ArrayList<>();
        for (int i = 0; i < rounds; i++) results.add(execute(implementation, ticks, false).timing());
        return List.copyOf(results);
    }

    private BotTestRun execute(Supplier<BotCalculation> factory, int ticks, boolean capture) {
        if (ticks < 1) throw new IllegalArgumentException("ticks must be positive");
        BotCalculation calculation = Objects.requireNonNull(factory.get(), "algorithm factory result");
        BotTargetSelection selector = Objects.requireNonNull(targetSelection.get(), "target selection factory result");
        long[] targetNanos = new long[ticks];
        long[] targetCalls = {0};
        List<BotTestRun.TargetDecision> decisions = new ArrayList<>();
        long[] botNanos = new long[ticks];
        long[] roundNanos = new long[ticks];
        int[] index = {0};
        BotCalculation measured = (situation, tickId, goals) -> {
            long start = System.nanoTime();
            calculation.calculate(situation, tickId, goals);
            botNanos[index[0]] = System.nanoTime() - start;
        };
        BotTargetSelection measuredSelection = (situation, tickId) -> {
            long start = System.nanoTime();
            String selected = selector.select(situation, tickId);
            targetNanos[index[0]] += System.nanoTime() - start;
            targetCalls[0]++;
            if (capture) {
                Ship target = situation.resolve(selected);
                decisions.add(new BotTestRun.TargetDecision(tickId, situation.timeSeconds(), situation.observerId(),
                        situation.currentTargetId(), selected, target == null ? -1 : target.lifeId(), situation.candidateCount()));
            }
            return selected;
        };
        GameSession session = new GameSession(definition.setup(), threads, parallelNavigation, measured, measuredSelection);
        NavigationService navigation = new NavigationService();
        RadarService radar = new RadarService();
        initialize(session, navigation);
        List<BotTestRun.Frame> frames = new ArrayList<>();
        if (capture) frames.add(BotTestRun.frame(session.snapshot()));
        for (int tick = 0; tick < ticks; tick++) {
            index[0] = tick;
            long start = System.nanoTime();
            session.update(TICK_SECONDS, radar, navigation, session.worldMap());
            roundNanos[tick] = System.nanoTime() - start;
            // Match the normal snapshot lifecycle (also expires short-lived shot traces),
            // but keep its allocation/work outside BOTH measured durations.
            GameSnapshot snapshot = session.snapshot();
            if (capture) frames.add(BotTestRun.frame(snapshot));
        }
        return new BotTestRun(script, frames, decisions, BotTestRun.Timing.of(botNanos, targetNanos, roundNanos, targetCalls[0]));
    }

    private void initialize(GameSession session, NavigationService navigation) {
        for (ScenarioInitialState initial : definition.initialStates()) {
            Ship ship = session.calculationShips().stream().filter(s -> s.id().equals(initial.id())).findFirst().orElseThrow();
            ShipSnapshot s = ship.snapshot();
            double y = ship.isSubmarine() ? switch (initial.depth()) {
                case "periscope" -> -1.86 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
                case "submerged" -> SeaBattleGameConfig.SUBMARINE_DEEP_Y;
                default -> 0;
            } : s.y();
            if (navigation.isShipBlocked(ship.position(), ship.heading(), session.worldMap()) && !ship.isScoutPlane())
                throw new IllegalArgumentException("Initial ship hull overlaps land: " + ship.id());
            ship.applyPlayerState(new PlayerStateUpdate(s.controlledBy(), s.teamId(), s.x(), s.z(), s.heading(),
                    initial.speed(), 0, s.engineOrder(), s.rudderDegrees(), 0, true, s.vehicleType(), y, 0,
                    null, null, null, null, initial.depth()), navigation, session.worldMap());
        }
    }
}
