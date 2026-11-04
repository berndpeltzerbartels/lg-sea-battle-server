package one.xis.seabattle.game;

/** Only the unrounded geometric history needed for swept collisions, not an output DTO. */
record HullPose(Vehicle vehicle, long lifeId, double x, double y, double z, double heading) {
  HullPose(Vehicle vehicle) {
    this(
        vehicle,
        vehicle.lifeId(),
        vehicle.position().x(),
        vehicle.y(),
        vehicle.position().z(),
        vehicle.heading());
  }

  HullPose interpolate(HullPose next, double fraction) {
    return new HullPose(
        next.vehicle,
        next.lifeId,
        x + (next.x - x) * fraction,
        y + (next.y - y) * fraction,
        z + (next.z - z) * fraction,
        heading + MathSupport.normalizeAngle(next.heading - heading) * fraction);
  }
}
