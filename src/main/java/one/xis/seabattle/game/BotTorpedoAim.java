package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

/** Constant-velocity intercept for fixed forward tubes; no world scan or copied vehicles. */
record BotTorpedoAim(double heading, Vector2 targetPosition, double angularVelocity) {
  static double speed(Vehicle shooter) {
    return SHIP_TORPEDO_BASE_SPEED + Math.max(0, shooter.speed()) * SHIP_TORPEDO_SPEED_GAIN;
  }

  static BotTorpedoAim forTarget(Vehicle shooter, Vehicle target) {
    return forTarget(shooter, target, BOT_FIRE_MAX_RANGE);
  }

  static BotTorpedoAim forTarget(Vehicle shooter, Vehicle target, double maxRange) {
    return forTarget(shooter, target, maxRange, 0);
  }

  static BotTorpedoAim forTarget(Vehicle shooter, Vehicle target, double maxRange, double headingError) {
    Vector2 velocity = Vector2.fromHeading(target.heading()).scale(target.speed());
    Vector2 origin = shooter.position();
    double speed = speed(shooter);
    double flight = 0;
    Vector2 future = target.position();
    double heading = shooter.heading();
    // Account for the forward tube offset without changing the actual launch direction.
    for (int i = 0; i < 3; i++) {
      Vector2 offset = target.position().subtract(origin);
      double a = velocity.x() * velocity.x() + velocity.z() * velocity.z() - speed * speed;
      double b = 2 * (offset.x() * velocity.x() + offset.z() * velocity.z());
      double c = offset.x() * offset.x() + offset.z() * offset.z();
      double discriminant = b * b - 4 * a * c;
      if (discriminant < 0) return null;
      if (Math.abs(a) < 1e-8) flight = Math.abs(b) < 1e-8 ? 0 : -c / b;
      else {
        double first = (-b - Math.sqrt(discriminant)) / (2 * a);
        double second = (-b + Math.sqrt(discriminant)) / (2 * a);
        flight = first > 0 && second > 0 ? Math.min(first, second) : Math.max(first, second);
      }
      if (flight < 0 || flight * speed > maxRange) return null;
      future = target.position().add(velocity.scale(flight));
      Vector2 toFuture = future.subtract(shooter.position());
      heading = Math.atan2(toFuture.x(), toFuture.z());
      origin = shooter.position().add(Vector2.fromHeading(heading)
          .scale((shooter.isSubmarine() ? 5.45 : 5.0) * TORPEDO_BOAT_MODEL_SCALE));
    }
    Vector2 offset = future.subtract(origin);
    Vector2 ownVelocity = Vector2.fromHeading(shooter.heading()).scale(shooter.speed());
    Vector2 relative = velocity.subtract(ownVelocity);
    // Differentiate the intercept equation: the helm follows the changing bearing rather
    // than waiting for an error to build up. Acceleration/turns are re-evaluated next tick.
    double denominator = offset.x() * velocity.x() + offset.z() * velocity.z() - speed * speed * flight;
    double flightChange = Math.abs(denominator) < 1e-8 ? 0
        : -(offset.x() * relative.x() + offset.z() * relative.z()) / denominator;
    Vector2 change = relative.add(velocity.scale(flightChange));
    double square = offset.x() * offset.x() + offset.z() * offset.z();
    double angularVelocity = square < 1 ? 0
        : (offset.z() * change.x() - offset.x() * change.z()) / square;
    return new BotTorpedoAim(MathSupport.normalizeAngle(heading + headingError), future, angularVelocity);
  }
}
