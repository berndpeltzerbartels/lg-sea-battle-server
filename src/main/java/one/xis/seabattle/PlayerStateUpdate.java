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
        double clientTime,
        boolean debugTeleport,
        String vehicleType,
        double y,
        double verticalSpeed,
        Double flakYaw,
        Double flakPitch,
        Double cannonYaw,
        Double cannonPitch
) {
    public PlayerStateUpdate(String playerId, String teamId, double x, double z, double heading, double speed,
                             double turnVelocity, int engineOrder, int rudderDegrees, double clientTime,
                             boolean debugTeleport, String vehicleType) {
        this(playerId, teamId, x, z, heading, speed, turnVelocity, engineOrder, rudderDegrees, clientTime,
                debugTeleport, vehicleType, 0, 0, null, null, null, null);
    }

    public PlayerStateUpdate(String playerId, String teamId, double x, double z, double heading, double speed,
                             double turnVelocity, int engineOrder, int rudderDegrees, double clientTime,
                             boolean debugTeleport) {
        this(playerId, teamId, x, z, heading, speed, turnVelocity, engineOrder, rudderDegrees, clientTime,
                debugTeleport, "torpedo-boat", 0, 0, null, null, null, null);
    }

    public PlayerStateUpdate(String playerId, String teamId, double x, double z, double heading, double speed,
                             double turnVelocity, int engineOrder, int rudderDegrees, double clientTime,
                             boolean debugTeleport, String vehicleType, double y) {
        this(playerId, teamId, x, z, heading, speed, turnVelocity, engineOrder, rudderDegrees, clientTime,
                debugTeleport, vehicleType, y, 0, null, null, null, null);
    }

    public PlayerStateUpdate(String playerId, String teamId, double x, double z, double heading, double speed,
                             double turnVelocity, int engineOrder, int rudderDegrees, double clientTime,
                             boolean debugTeleport, String vehicleType, double y, double verticalSpeed) {
        this(playerId, teamId, x, z, heading, speed, turnVelocity, engineOrder, rudderDegrees, clientTime,
                debugTeleport, vehicleType, y, verticalSpeed, null, null, null, null);
    }
}
