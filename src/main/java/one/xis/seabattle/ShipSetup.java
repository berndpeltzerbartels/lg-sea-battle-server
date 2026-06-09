package one.xis.seabattle;

record ShipSetup(
        String id,
        String teamId,
        Vector2 position,
        double heading,
        String controlledBy,
        int engineOrder,
        int rudderDegrees,
        double nextFireDelaySeconds
) {
}
