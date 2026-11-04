package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BotTorpedoAimTest {
  private final WorldMap water = new WorldMap(1, List.of());
  private final NavigationService navigation = new NavigationService();

  private GameSession session(String shooterType, double targetSpeed, double targetHeading) {
    var setup = new GameSetup("aim", water, List.of(
        new FleetSetup("light", List.of(new VehicleSetup("shooter", "light", new Vector2(0, 0),
            0, "captain", ENGINE_STOP, 0, 0, shooterType, 0))),
        new FleetSetup("dark", List.of(new VehicleSetup("target", "dark", new Vector2(0, 300),
            targetHeading, "opponent", ENGINE_HALF, 0, 9999, "torpedo-boat", 0)))), List.of());
    var session = new GameSession(setup);
    vehicle(session, "target").speed = targetSpeed;
    return session;
  }

  private Vehicle vehicle(GameSession session, String id) {
    return session.calculationShips().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
  }

  @Test void crossingTargetRequiresLeadAndTheActualTorpedoHitsItForBothWaterVehicles() {
    for (String type : List.of("torpedo-boat", "submarine")) for (double side : new double[]{-1, 1}) {
      var session = session(type, EngineOrders.speedFor(ENGINE_HALF), side * Math.PI / 2);
      Vehicle shooter = vehicle(session, "shooter"), target = vehicle(session, "target");
      if (shooter instanceof Submarine) {
        shooter.applyPlayerState(new PlayerStateUpdate("captain", "light", 0, 0, 0, 0, 0,
            ENGINE_STOP, 0, 0, false, "submarine", -5.58, 0,
            null, null, null, null, "periscope"), navigation, water);
      }
      assertFalse(session.fireTorpedoAtTarget(shooter, target, 10, 0),
          "A course through the present position would miss the moving target");
      assertTrue(shooter.canFire(0), "Withholding a shot must not consume reload");
      BotTorpedoAim aim = BotTorpedoAim.forTarget(shooter, target);
      assertNotNull(aim);
      assertTrue(aim.heading() * side > Math.toRadians(5), "Aim ahead of the crossing ship");
      shooter.heading = aim.heading();
      assertTrue(session.fireTorpedoAtTarget(shooter, target, 10, 0),
          "The firing gate must accept the same lead that navigation uses");
      for (int tick = 0; tick < 150 && "active".equals(target.state()); tick++)
        session.update(.1, new RadarService(), navigation, water);
      assertNotEquals("active", target.state(), "Real projectile physics must confirm the intercept");
    }
  }

  @Test void stationaryTargetNeedsNoLeadAndAnUnreachableTargetHasNoSolution() {
    var session = session("torpedo-boat", 0, Math.PI / 2);
    Vehicle shooter = vehicle(session, "shooter"), target = vehicle(session, "target");
    assertEquals(0, BotTorpedoAim.forTarget(shooter, target).heading(), 1e-10);
    assertTrue(session.fireTorpedoAtTarget(shooter, target, 10, 0));
    target.heading = 0;
    target.speed = 60;
    assertNull(BotTorpedoAim.forTarget(shooter, target));
  }

  @Test void preciseHelmTracksChangingAimWithoutTheOldSeveralDegreeLag() {
    for (double rotation : new double[]{-.04, 0, .02, .04}) {
      var boat = new TorpedoBoat("helm", "light", new Vector2(0, 0), 0, "bot");
      boat.speed = EngineOrders.speedFor(ENGINE_HALF);
      for (int tick = 0; tick < 600; tick++) {
        double desired = .35 + rotation * tick * .1;
        var aim = new BotTorpedoAim(desired, new Vector2(0, 300), rotation);
        boat.applyCommand(ENGINE_HALF, BotHelm.aimRudder(
            boat.heading(), boat.turnVelocity(), boat.speed(), aim));
        boat.update(.1, navigation, water);
      }
      double error = Math.abs(MathSupport.normalizeAngle(.35 + rotation * 60 - boat.heading()));
      assertTrue(error < Math.toRadians(.3), "Accurate tracking, error=" + Math.toDegrees(error));
    }
  }

  @Test void submarineKeepsSteerageWhileStillAimingOrTrackingACrossingTarget() {
    var session = session("submarine", 0, Math.PI / 2);
    var sub = (Submarine) vehicle(session, "shooter");
    Vehicle target = vehicle(session, "target");
    sub.heading = -Math.toRadians(3);
    assertEquals(ENGINE_ONE_THIRD, SubmarineBotCalculation.holdPeriscopeAttackDistance(
        sub, target, 300, Math.toRadians(3)));
    sub.heading = 0;
    assertEquals(ENGINE_STOP, SubmarineBotCalculation.holdPeriscopeAttackDistance(sub, target, 300, 0));
    target.speed = EngineOrders.speedFor(ENGINE_HALF);
    sub.heading = BotTorpedoAim.forTarget(sub, target).heading();
    assertEquals(ENGINE_ONE_THIRD, SubmarineBotCalculation.holdPeriscopeAttackDistance(
        sub, target, 300, -sub.heading()));
  }

  @Test void productionBotsAlignAndHitACrossingTargetInBothExecutionModes() {
    for (boolean parallel : List.of(false, true)) for (String type : List.of("torpedo-boat", "submarine")) {
      var setup = new GameSetup("production-aim", water, List.of(
          new FleetSetup("light", List.of(new VehicleSetup("shooter", "light", new Vector2(0, 0),
              -.2, "bot", ENGINE_HALF, 0, 0, type, 0))),
          new FleetSetup("dark", List.of(new VehicleSetup("target", "dark", new Vector2(0, 350),
              Math.PI / 2, "opponent", ENGINE_HALF, 0, 9999, "torpedo-boat", 0)))), List.of());
      var session = new GameSession(setup, 1, parallel, new StandardBotCalculation(), new StandardBotTargetSelection());
      Vehicle target = vehicle(session, "target");
      target.speed = EngineOrders.speedFor(ENGINE_HALF);
      Vehicle shooter = vehicle(session, "shooter");
      boolean checkedReload = false;
      for (int tick = 0; tick < 300 && "active".equals(target.state()); tick++) {
        session.update(.1, new RadarService(), navigation, water);
        if (!checkedReload && shooter.nextFireTime > session.nowSeconds) {
          double pause = shooter.nextFireTime - session.nowSeconds;
          assertTrue(pause >= 10.5 && pause <= 13.5, "Keep the original bot reload: " + pause);
          assertFalse(shooter.canFire(session.nowSeconds + 10));
          assertTrue(shooter.canFire(shooter.nextFireTime));
          checkedReload = true;
        }
      }
      assertTrue(checkedReload, "Check the reload of an actual production bot shot");
      assertNotEquals("active", target.state(), "Production " + type + ", parallel=" + parallel);
      assertTrue(session.snapshot().torpedoImpacts().stream()
          .anyMatch(hit -> "ship-hit".equals(hit.reason()) && "target".equals(hit.targetShipId())),
          "Hit with a torpedo rather than a collision");
    }
  }
}
