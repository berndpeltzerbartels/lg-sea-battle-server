package one.xis.seabattle;

public record PlayerStateUpdate(
        String playerId,
        String teamId,
        double x,
        double z,
        double heading,
        double speed,
        double turnVelocity,
        int engineOrder,
        int rudderDegrees,
        double clientTime
) {
}
