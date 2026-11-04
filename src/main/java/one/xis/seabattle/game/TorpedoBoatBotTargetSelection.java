package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.Map;
import java.util.Objects;

/** Standard torpedo-boat contacts, U-boat hunt and remembered attack courses. */
public final class TorpedoBoatBotTargetSelection implements VehicleBotTargetSelection<TorpedoBoat> {
  private final WaterBotTargetSelection waterTargets;

  public TorpedoBoatBotTargetSelection() {
    this(new WaterBotTargetSelection());
  }

  TorpedoBoatBotTargetSelection(WaterBotTargetSelection waterTargets) {
    this.waterTargets = Objects.requireNonNull(waterTargets);
  }

  @Override
  public Vehicle select(TorpedoBoat boat, BotTargetSituation situation, long tickId) {
    return waterTargets.select(boat, situation, tickId);
  }

  @Override
  public void observe(TorpedoBoat boat, BotTargetSituation situation, long tickId) {
    waterTargets.observe(situation, tickId);
  }

  @Override
  public void sonarContact(
      TorpedoBoat boat, BotTargetSituation situation, Submarine target, long tickId) {
    waterTargets.sonarContact(situation, target, tickId);
  }

  @Override
  public Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    return waterTargets.submarineDetections(now);
  }

  @Override
  public BotGoal goal(
      TorpedoBoat boat, BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    updateOrders(boat, contact, situation.timeSeconds());
    return navigationGoal(situation, boat, contact, advanceSearch);
  }

  private void updateOrders(TorpedoBoat boat, Vehicle contact, double now) {
    if (boat.submarineSearch() != null && !boat.submarineSearch().active(now))
      boat.submarineSearch(null);
    if (boat.botSubmarineAttackCourse != null && now >= boat.botSubmarineAttackCourse.until()) {
      boat.botSubmarineAttackCourse = null;
      if (contact == null) boat.botNavigation.target = null;
    }
    if (contact != null) {
      double distance = boat.position().distanceTo(contact.position());
      if (contact instanceof Submarine
          && contact.isHumanControlled()
          && distance <= BotNavigation.RADAR_RING) {
        boat.botSubmarineAttackCourse =
            new SurfaceVehicle.SubmarineAttackCourse(
                contact.id(),
                contact.position(),
                contact.heading(),
                now + BOT_SUBMARINE_ATTACK_CONTINUATION_SECONDS);
      } else boat.botSubmarineAttackCourse = null;
    }
  }

  private BotGoal navigationGoal(
      BotTargetSituation situation, TorpedoBoat boat, Vehicle contact, boolean advanceSearch) {
    var search = boat.submarineSearch();
    boolean localContact =
        contact != null
            && (boat.position().distanceTo(contact.position()) <= BotNavigation.ENCOUNTER_RANGE
                || contact.isSubmarine() && contact.isHumanControlled());
    if (contact != null && (search == null || localContact)) {
      return BotGoal.enemy(contact);
    }
    var attack = boat.botSubmarineAttackCourse;
    if (attack != null) {
      Vector2 forward = Vector2.fromHeading(attack.heading());
      Vector2 offset = boat.position().subtract(attack.position());
      double along = offset.x() * forward.x() + offset.z() * forward.z();
      Vector2 point = attack.position().add(forward.scale(Math.max(0, along) + 60));
      return new BotGoal(
          BotGoal.Kind.LAST_KNOWN_COURSE,
          attack.targetId(),
          boat.botNavigation.targetLife,
          point.x(),
          point.z());
    }
    if (search != null) {
      Vector2 point = advanceSearch ? search.destination(boat) : search.currentDestination();
      return new BotGoal(BotGoal.Kind.SEARCH, null, -1, point.x(), point.z());
    }
    return waterTargets.fallbackGoal(boat, situation);
  }
}
