package one.xis.seabattle.game;

/** Flat, immutable wire format shared with the browser; mutable subtype state lives in Vehicle. */
public record VehicleSnapshot(
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
        String vehicleType,
        double y,
        double flakYaw,
        double flakPitch,
        double cannonYaw,
        double cannonPitch,
        String depthState,
        long lifeId
) {
}
