package one.xis.seabattle.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Developer CLI; deliberately absent from the HTTP application. */
public final class BotScenarioMain {
    public static void main(String[] args) throws Exception {
        BotScenario scenario = BotScenario.parse(Files.readString(Path.of(args[0])));
        Path output = Path.of(args[1]);
        int ticks = Integer.parseInt(args[2]);
        Supplier<BotCalculation> factory = implementation(args[3], BotCalculation.class, BotCalculation::standard);
        String selectionClass = args.length > 5 ? args[5] : "standard";
        scenario = scenario.withTargetSelection(implementation(selectionClass, BotTargetSelection.class,
                BotTargetSelection::standard));
        BotTestRun run = scenario.run(factory, ticks);
        run.writeReport(output);
        System.out.println("Playback: " + output.resolve("index.html").toAbsolutePath());
        int rounds = Integer.parseInt(args[4]);
        if (rounds < 0) throw new IllegalArgumentException("rounds must not be negative");
        if (rounds > 0) {
            StringBuilder csv = new StringBuilder("round,botMeanMs,botP95Ms,botP99Ms,targetMeanMs,targetP95Ms,targetP99Ms,targetCalls,totalMeanMs,totalP95Ms,totalP99Ms\n");
            int round = 0;
            for (BotTestRun.Timing t : scenario.benchmark(factory, ticks, 2, rounds)) {
                csv.append(++round).append(',').append(t.bots().meanMs()).append(',').append(t.bots().p95Ms())
                        .append(',').append(t.bots().p99Ms()).append(',').append(t.targetSelection().meanMs())
                        .append(',').append(t.targetSelection().p95Ms()).append(',').append(t.targetSelection().p99Ms())
                        .append(',').append(t.targetSelectionCalls()).append(',').append(t.completeRound().meanMs())
                        .append(',').append(t.completeRound().p95Ms()).append(',').append(t.completeRound().p99Ms()).append('\n');
            }
            Files.writeString(output.resolve("benchmark.csv"), csv);
            System.out.print(csv);
        }
    }
    private static <T> Supplier<T> implementation(String name, Class<T> type, Supplier<T> standard) {
        if (name.equals("standard")) return standard;
        return () -> {
            try {
                return type.cast(Class.forName(name).getConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalArgumentException("Expected " + type.getSimpleName()
                        + " with a public no-argument constructor: " + name, e);
            }
        };
    }
}
