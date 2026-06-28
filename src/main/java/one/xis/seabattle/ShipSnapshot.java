package one.xis.seabattle;

public record ShipSnapshot(
        String id,
        String teamId,
        double x,
        double z,
        double heading,
        double speed,
        double turnVelocity,
        int rudderDegrees,
        int engineOrder,
        String state,
        String controlledBy,
        int torpedoesRemaining,
        String vesselType,
        String depthState
) {
}
