package one.xis.seabattle.game;

import java.util.*;

public class BotModelAllocationProbe {
  public static void main(String[] args) {
    int count = args.length > 0 ? Integer.parseInt(args[0]) : 40;
    List<VehicleSetup> light = new ArrayList<>(), dark = new ArrayList<>();
    for (int i = 0; i < count / 2; i++) {
      String type = i % 4 == 0 ? "submarine" : "torpedo-boat";
      light.add(
          new VehicleSetup(
              "l" + i,
              "light",
              new Vector2((i % 10) * 95 - 450, (i / 10) * 100 - 350),
              0,
              "bot",
              5,
              0,
              0,
              type,
              0));
      dark.add(
          new VehicleSetup(
              "d" + i,
              "dark",
              new Vector2((i % 10) * 95 - 450, (i / 10) * 100 + 350),
              Math.PI,
              "bot",
              5,
              0,
              0,
              type,
              0));
    }
    GameSetup setup =
        new GameSetup(
            "allocation-probe",
            new WorldMap(1, List.of()),
            List.of(new FleetSetup("light", light), new FleetSetup("dark", dark)),
            List.of(new Vector2(-1300, 0), new Vector2(1300, 0)));
    List<Double> means = new ArrayList<>(), p95s = new ArrayList<>();
    GameSession last = null;
    for (int round = 0; round < 7; round++) {
      GameSession session = new GameSession(setup);
      RadarService radar = new RadarService();
      NavigationService navigation = new NavigationService();
      long[] samples = new long[600];
      for (int tick = 0; tick < samples.length; tick++) {
        long start = System.nanoTime();
        session.update(.1, radar, navigation, session.worldMap());
        samples[tick] = System.nanoTime() - start;
      }
      if (round > 1) {
        Arrays.sort(samples);
        means.add(Arrays.stream(samples).average().orElseThrow() / 1e6);
        p95s.add(samples[(int) (samples.length * .95)] / 1e6);
      }
      last = session;
    }
    System.out.println("MODEL_FINISHED vehicles=" + count + " meanMs=" + means + " p95Ms=" + p95s);
    System.out.println("OUTPUT_BEGIN");
    last.snapshot();
    System.out.println("OUTPUT_END");
  }
}
