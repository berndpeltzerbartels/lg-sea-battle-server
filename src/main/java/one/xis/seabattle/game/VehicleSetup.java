package one.xis.seabattle.game;

record VehicleSetup(
        String id,
        String teamId,
        Vector2 position,
        double heading,
        String controlledBy,
        int engineOrder,
        int rudderDegrees,
        double nextFireDelaySeconds,
        String vehicleType,
        double y
) {
}
