package one.xis.seabattle.game;

/** Cheap navigation envelope, built once per round; detailed collision stays in WreckPhysics. */
record BotWreckObstacle(double x, double z, double bottom, double top) {
    static BotWreckObstacle of(Wreck wreck, double age) {
        WreckPhysics.Pose now = WreckPhysics.pose(wreck, age);
        WreckPhysics.Pose soon = WreckPhysics.pose(wreck, Math.min(age + 3, wreck.sinkSeconds()));
        // Enclose the tilted hull, including the waterline-to-deck offset.
        double extent = 4.2 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE * Math.abs(Math.sin(soon.pitch()))
                + .79 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE * Math.abs(Math.sin(soon.roll()))
                + .74 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
        return new BotWreckObstacle(now.x(), now.z(), soon.y() - extent, now.y() + extent);
    }

    boolean courseClear(Vehicle vehicle, BotNavigation.Course course, double range, double horizon) {
        // Include a descending wreck above a submerged boat, but release the surface once clear.
        if (vehicle.y() + 3 < bottom || vehicle.y() - 3 > top) return true;
        double dx = x - vehicle.position().x(), dz = z - vehicle.position().z();
        double distance2 = dx * dx + dz * dz;
        if (distance2 > range * range) return true;
        double speed = Math.max(Math.abs(vehicle.speed()), Math.max(0, EngineOrders.speedFor(course.engineOrder())));
        double vx = Math.sin(course.heading()) * speed, vz = Math.cos(course.heading()) * speed;
        double approach = dx * vx + dz * vz;
        // A boat already close must still be allowed to move out of danger.
        if (approach <= 0) return true;
        double time = MathSupport.clamp(approach / Math.max(.001, speed * speed), 0, horizon);
        double closestX = dx - vx * time, closestZ = dz - vz * time;
        double clearance = (vehicle instanceof Submarine ? 5.02 : 4.2) * SeaBattleGameConfig.TORPEDO_BOAT_SCALE
                + 4.2 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE + 3;
        return closestX * closestX + closestZ * closestZ >= clearance * clearance;
    }
}
