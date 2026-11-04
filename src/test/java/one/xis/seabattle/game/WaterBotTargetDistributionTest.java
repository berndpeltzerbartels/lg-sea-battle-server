package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaterBotTargetDistributionTest {
  private final NavigationService navigation = new NavigationService();
  private final RadarService radar = new RadarService();

  private BotTargetSituation situation(
      SurfaceVehicle observer, List<Vehicle> targets, Map<String, Integer> assignments) {
    var setup = new GameSetup("distribution", new WorldMap(1, List.of()), List.of(), List.of());
    var session = new GameSession(setup);
    List<Vehicle> all = new ArrayList<>(targets);
    all.add(observer);
    return new BotTargetSituation(
        session,
        observer,
        targets,
        radar.visibilityCache(session.worldMap(), all),
        Map.of(),
        assignments);
  }

  private Vehicle target(String id, double distance, boolean human) {
    return new TorpedoBoat(
        id, "dark", new Vector2(0, distance), 0, human ? "captain-" + id : "bot");
  }

  @Test
  void thirdAttackerMayChooseAnAlternativeUpToThirtyPercentFartherForBothWaterTypes() {
    for (SurfaceVehicle observer :
        List.of(
            new TorpedoBoat("boat", "light", new Vector2(0, 0), 0, "bot"),
            new Submarine("submarine", "light", new Vector2(0, 0), 0, "bot"))) {
      var preferred = target("preferred", 100, false);
      var alternative = target("alternative", 130, false);
      var selection = new StandardBotTargetSelection();
      assertSame(
          preferred,
          selection.select(
              situation(observer, List.of(preferred, alternative), Map.of("preferred", 1)), 1));
      assertSame(
          alternative,
          selection.select(
              situation(observer, List.of(preferred, alternative), Map.of("preferred", 2)), 1));
      alternative.position = new Vector2(0, 130.01);
      assertSame(
          preferred,
          selection.select(
              situation(observer, List.of(preferred, alternative), Map.of("preferred", 2)), 1));
    }
  }

  @Test
  void choosesNearestAvailableAlternativeAndKeepsPreferredWhenEverythingIsCrowded() {
    var observer = new TorpedoBoat("boat", "light", new Vector2(0, 0), 0, "bot");
    var preferred = target("preferred", 100, false);
    var nearer = target("nearer", 110, false);
    var farther = target("farther", 120, false);
    var selection = new StandardBotTargetSelection();
    assertSame(
        nearer,
        selection.select(
            situation(observer, List.of(preferred, farther, nearer), Map.of("preferred", 2)), 1));
    assertSame(
        farther,
        selection.select(
            situation(
                observer, List.of(preferred, farther, nearer), Map.of("preferred", 2, "nearer", 2)),
            1));
    assertSame(
        preferred,
        selection.select(
            situation(
                observer,
                List.of(preferred, farther, nearer),
                Map.of("preferred", 2, "nearer", 2, "farther", 2)),
            1));
  }

  @Test
  void ownExistingChoiceDoesNotCountAsAnotherAttacker() {
    var observer = new TorpedoBoat("boat", "light", new Vector2(0, 0), 0, "bot");
    var preferred = target("preferred", 100, false);
    var alternative = target("alternative", 120, false);
    observer.botNavigation.target = preferred;
    observer.botNavigation.targetLife = preferred.lifeId();
    var context = situation(observer, List.of(preferred, alternative), Map.of("preferred", 2));
    assertEquals(1, context.otherAssignedWaterAttackers(preferred));
    assertSame(preferred, new StandardBotTargetSelection().select(context, 1));
  }

  @Test
  void humanPriorityIsNeverReplacedByABotButMaySpreadBetweenHumans() {
    var observer = new TorpedoBoat("boat", "light", new Vector2(0, 0), 0, "bot");
    var human = target("human", 1000, true);
    var bot = target("bot", 400, false);
    var otherHuman = target("otherHuman", 1250, true);
    var selection = new StandardBotTargetSelection();
    assertSame(
        human, selection.select(situation(observer, List.of(bot, human), Map.of("human", 2)), 1));
    assertSame(
        otherHuman,
        selection.select(
            situation(observer, List.of(bot, human, otherHuman), Map.of("human", 2)), 1));
  }

  @Test
  void huntAndRememberedSubmarineApproachKeepTheirExistingSelectionAndGoal() {
    var observer = new TorpedoBoat("boat", "light", new Vector2(0, 0), 0, "bot");
    var preferred = target("preferred", 100, false);
    var alternative = target("alternative", 120, false);
    var search =
        new BotSubmarineHunt.Search(
            new BotSubmarineHunt.Area(new Vector2(300, 300), 60), observer.position());
    observer.submarineSearch(search);
    var context = situation(observer, List.of(preferred, alternative), Map.of("preferred", 5));
    var selection = new StandardBotTargetSelection();
    assertSame(preferred, selection.select(context, 1));
    assertEquals(BotGoal.Kind.SEARCH, selection.goal(context, null, false).kind());
    assertSame(search, observer.submarineSearch());
    observer.submarineSearch(null);
    var hidden = new Submarine("hidden", "dark", new Vector2(0, 100), 0, "bot");
    hidden.botDepth("submerged");
    observer.botNavigation.target = hidden;
    observer.botNavigation.targetLife = hidden.lifeId();
    observer.botSubmarineAttackCourse =
        new SurfaceVehicle.SubmarineAttackCourse(hidden.id(), hidden.position(), 0, 10);
    context = situation(observer, List.of(preferred, alternative), Map.of(hidden.id(), 5));
    assertSame(hidden, selection.select(context, 1));
    assertEquals(BotGoal.Kind.LAST_KNOWN_COURSE, selection.goal(context, null, false).kind());
  }

  private VehicleSetup setup(String id, String team, double x, double z, boolean bot) {
    return new VehicleSetup(
        id,
        team,
        new Vector2(x, z),
        0,
        bot ? "bot" : "captain-" + id,
        2,
        0,
        9999,
        "torpedo-boat",
        0);
  }

  @Test
  void productionPassesPreviousAssignmentsUpdatesNewOnesAndKeepsTeamsSeparate() {
    var defaults = new StandardBotTargetSelection();
    Map<String, List<Integer>> counts = new HashMap<>();
    BotTargetSelection selection =
        (context, tick) -> {
          var candidate =
              context.candidates().stream()
                  .filter(v -> v.id().equals("target-a"))
                  .findFirst()
                  .orElseThrow();
          counts
              .computeIfAbsent(context.observerId(), ignored -> new ArrayList<>())
              .add(context.otherAssignedWaterAttackers(candidate));
          assertEquals(
              0,
              context.assignedAircraftCount(candidate),
              "Water counts must not masquerade as aircraft reservations");
          return defaults.select(context, tick);
        };
    var setup =
        new GameSetup(
            "production-distribution",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light",
                    List.of(
                        setup("one", "light", -5, 0, true),
                        setup("two", "light", 0, 0, true),
                        setup("three", "light", 5, 0, true))),
                new FleetSetup("green", List.of(setup("other-team", "green", 3000, 0, true))),
                new FleetSetup(
                    "dark",
                    List.of(
                        setup("target-a", "dark", 0, 100, false),
                        setup("target-b", "dark", 0, 125, false)))),
            List.of());
    var session = new GameSession(setup, 1, false, (s, tick, goals) -> {}, selection);
    for (int tick = 0; tick < 45; tick++) session.update(.1, radar, navigation, session.worldMap());
    assertEquals(0, counts.get("one").get(0));
    assertEquals(1, counts.get("two").get(0));
    assertEquals(2, counts.get("three").get(0));
    assertTrue(
        counts.get("one").subList(1, counts.get("one").size()).stream().allMatch(n -> n == 1),
        "Existing assignments survive staggered selection ticks, excluding self");
    assertTrue(counts.get("other-team").stream().allMatch(n -> n == 0));
    assertEquals(
        "target-a", ((SurfaceVehicle) session.calculationShips().get(0)).botNavigation.target.id());
    assertEquals(
        "target-a", ((SurfaceVehicle) session.calculationShips().get(1)).botNavigation.target.id());
    assertEquals(
        "target-b", ((SurfaceVehicle) session.calculationShips().get(2)).botNavigation.target.id());
  }
}
