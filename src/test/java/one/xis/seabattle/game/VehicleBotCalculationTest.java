package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VehicleBotCalculationTest {
  @Test
  void roundDispatchesOriginalVehiclesToTheirTypedImplementationsAndSkipsHumans() {
    var boatCalls = new AtomicInteger();
    var submarineCalls = new AtomicInteger();
    var planeCalls = new AtomicInteger();
    Vehicle[] original = new Vehicle[3];
    BotCalculation calculation =
        new StandardBotCalculation(
            (boat, situation, tick, goal) -> {
              assertSame(original[0], boat);
              assertSame(boat, situation.vehicles().get(0));
              assertEquals(BotGoal.Kind.PATROL, goal.kind());
              boatCalls.incrementAndGet();
              situation.steer(boat, 8, 100);
            },
            (submarine, situation, tick, goal) -> {
              assertSame(original[1], submarine);
              assertEquals("surface", submarine.depthState());
              submarineCalls.incrementAndGet();
              situation.steer(submarine, 8, 100);
            },
            (plane, situation, tick, goal) -> {
              assertSame(original[2], plane);
              assertEquals(BotGoal.Kind.NONE, goal.kind());
              planeCalls.incrementAndGet();
              situation.steer(plane, 7, 100);
            });
    var setup =
        new GameSetup(
            "typed-calculations",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light",
                    List.of(
                        new VehicleSetup(
                            "boat",
                            "light",
                            new Vector2(0, 0),
                            0,
                            "bot",
                            2,
                            0,
                            999,
                            "torpedo-boat",
                            0),
                        new VehicleSetup(
                            "submarine",
                            "light",
                            new Vector2(500, 0),
                            0,
                            "bot",
                            2,
                            0,
                            999,
                            "submarine",
                            0),
                        new VehicleSetup(
                            "plane",
                            "light",
                            new Vector2(1000, 0),
                            0,
                            "bot",
                            7,
                            0,
                            999,
                            "scout-plane",
                            170),
                        new VehicleSetup(
                            "human",
                            "light",
                            new Vector2(1500, 0),
                            0,
                            "captain",
                            2,
                            0,
                            999,
                            "torpedo-boat",
                            0)))),
            List.of());
    GameSession session =
        new GameSession(setup, 1, false, calculation, BotTargetSelection.standard());
    for (int i = 0; i < original.length; i++) original[i] = session.calculationShips().get(i);
    for (int i = 0; i < 4; i++)
      session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
    assertEquals(4, boatCalls.get());
    assertEquals(4, submarineCalls.get());
    assertEquals(4, planeCalls.get());
    assertEquals(35, original[0].rudderDegrees());
    assertEquals(45, original[1].rudderDegrees());
    assertEquals(35, original[2].rudderDegrees());
    assertEquals(0, session.calculationShips().get(3).speed());
  }

  @Test
  void defaultDispatcherRequiresAPreparedGoalForEveryBot() {
    String scenario =
        """
        cell: 100
        map:
        .1.
        objects:
        1: boat light bot
        """;
    assertThrows(
        NullPointerException.class,
        () ->
            BotScenario.parse(scenario)
                .run(
                    () ->
                        (situation, tick, goals) ->
                            new StandardBotCalculation().calculate(situation, tick, Map.of()),
                    1));
  }
}
