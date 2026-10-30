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
        Supplier<BotCalculation> factory = args[3].equals("standard") ? BotCalculation::standard : () -> {
            try {
                return (BotCalculation) Class.forName(args[3]).getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Algorithm needs a public no-argument constructor: " + args[3], e);
            }
        };
        BotTestRun run = scenario.run(factory, ticks);
        run.writeReport(output);
        System.out.println("Playback: " + output.resolve("index.html").toAbsolutePath());
        int rounds = Integer.parseInt(args[4]);
        if (rounds < 0) throw new IllegalArgumentException("rounds must not be negative");
        if (rounds > 0) {
            StringBuilder csv = new StringBuilder("round,botMeanMs,botP95Ms,botP99Ms,totalMeanMs,totalP95Ms,totalP99Ms\n");
            int round = 0;
            for (BotTestRun.Timing t : scenario.benchmark(factory, ticks, 2, rounds)) {
                csv.append(++round).append(',').append(t.bots().meanMs()).append(',').append(t.bots().p95Ms())
                        .append(',').append(t.bots().p99Ms()).append(',').append(t.completeRound().meanMs())
                        .append(',').append(t.completeRound().p95Ms()).append(',').append(t.completeRound().p99Ms()).append('\n');
            }
            Files.writeString(output.resolve("benchmark.csv"), csv);
            System.out.print(csv);
        }
    }
}
