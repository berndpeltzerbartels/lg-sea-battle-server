package one.xis.seabattle.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Captured every tick, including the initial state. Assertions can inspect any frame. */
public record BotTestRun(String script, List<Frame> frames, List<TargetDecision> targetDecisions, Timing timing) {
    public BotTestRun {
        frames = List.copyOf(frames);
        targetDecisions = List.copyOf(targetDecisions);
    }

    public record TargetDecision(long tickId, double seconds, String observerId, String previousTargetId,
                                 String targetId, long targetLifeId, int candidateCount) {}

    public record Frame(double seconds, List<VehicleSnapshot> ships, int torpedoes, int bombs, int wrecks) {
        public Frame { ships = List.copyOf(ships); }
        public VehicleSnapshot ship(char marker) {
            return ships.stream().filter(s -> s.id().endsWith("" + marker)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Vehicle absent in frame: " + marker));
        }
    }

    static Frame frame(GameSnapshot s) {
        return new Frame(s.t(), s.ships(), s.torpedoes().size(), s.bombs().size(), s.wrecks().size());
    }

    public Frame first() { return frames.get(0); }
    public Frame last() { return frames.get(frames.size() - 1); }

    public double displacement(char marker) {
        VehicleSnapshot a = first().ship(marker), b = last().ship(marker);
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    /** Distance of hull CENTERS at tick boundaries, not a collision proof. */
    public double minimumSeparation(char a, char b) {
        return frames.stream().mapToDouble(frame -> {
            VehicleSnapshot x = frame.ship(a), y = frame.ship(b);
            return Math.hypot(x.x() - y.x(), x.z() - y.z());
        }).min().orElseThrow();
    }

    public record Distribution(double meanMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) {
        static Distribution of(long[] samples) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            return new Distribution(Arrays.stream(sorted).average().orElseThrow() / 1e6,
                    percentile(sorted, .5), percentile(sorted, .95), percentile(sorted, .99), sorted[sorted.length - 1] / 1e6);
        }
        private static double percentile(long[] sorted, double p) {
            return sorted[(int) Math.ceil(p * sorted.length) - 1] / 1e6;
        }
    }

    public record Timing(Distribution bots, Distribution targetSelection, Distribution completeRound, long targetSelectionCalls) {
        static Timing of(long[] bots, long[] targets, long[] rounds, long calls) {
            return new Timing(Distribution.of(bots), Distribution.of(targets), Distribution.of(rounds), calls);
        }
    }

    public void writeReport(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("scenario.txt"), script);
        StringBuilder csv = new StringBuilder("seconds,id,team,type,x,z,headingDegrees,speed,engine,rudder,depth,y,state,lifeId\n");
        for (Frame frame : frames) for (VehicleSnapshot s : frame.ships()) {
            csv.append(String.format(Locale.ROOT, "%.1f,%s,%s,%s,%.4f,%.4f,%.3f,%.4f,%d,%d,%s,%.3f,%s,%d%n",
                    frame.seconds(), s.id(), s.teamId(), s.vehicleType(), s.x(), s.z(), Math.toDegrees(s.heading()),
                    s.speed(), s.engineOrder(), s.rudderDegrees(), s.depthState(), s.y(), s.state(), s.lifeId()));
        }
        Files.writeString(directory.resolve("trace.csv"), csv);
        StringBuilder choices = new StringBuilder("tick,seconds,observer,previousTarget,target,targetLife,candidates\n");
        for (TargetDecision choice : targetDecisions) {
            choices.append(String.format(Locale.ROOT, "%d,%.4f,%s,%s,%s,%d,%d%n", choice.tickId(), choice.seconds(),
                    choice.observerId(), choice.previousTargetId() == null ? "" : choice.previousTargetId(),
                    choice.targetId() == null ? "" : choice.targetId(), choice.targetLifeId(), choice.candidateCount()));
        }
        Files.writeString(directory.resolve("targets.csv"), choices);
        Files.writeString(directory.resolve("index.html"), BotScenarioReport.html(this));
    }
}
