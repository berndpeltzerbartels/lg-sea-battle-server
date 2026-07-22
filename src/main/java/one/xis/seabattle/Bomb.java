package one.xis.seabattle;

final class Bomb {

    private static final double GRAVITY = 14.0;
    private static final double SEA_LEVEL = 0.0;

    private final String id;
    private final String teamId;
    private final String shipId;
    private Vector2 position;
    private final double heading;
    private final double horizontalSpeed;
    private final double droppedAtSeconds;
    private double releaseDelaySeconds;
    private double altitude;
    private double verticalSpeed;
    private String state = "falling";

    Bomb(String id, String teamId, String shipId, Vector2 position, double altitude, double heading,
         double horizontalSpeed, double droppedAtSeconds, double releaseDelaySeconds) {
        this(id, teamId, shipId, position, altitude, heading, horizontalSpeed, 0, droppedAtSeconds, releaseDelaySeconds);
    }

    Bomb(String id, String teamId, String shipId, Vector2 position, double altitude, double heading,
         double horizontalSpeed, double initialVerticalSpeed, double droppedAtSeconds, double releaseDelaySeconds) {
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
        this.position = position;
        this.altitude = Math.max(SEA_LEVEL, altitude);
        this.heading = heading;
        this.horizontalSpeed = Math.max(0, horizontalSpeed);
        this.verticalSpeed = initialVerticalSpeed;
        this.droppedAtSeconds = droppedAtSeconds;
        this.releaseDelaySeconds = Math.max(0, releaseDelaySeconds);
        this.state = this.releaseDelaySeconds > 0 ? "pending" : "falling";
    }

    String id() {
        return id;
    }

    String teamId() {
        return teamId;
    }

    String shipId() {
        return shipId;
    }

    Vector2 position() {
        return position;
    }

    String state() {
        return state;
    }

    void detonate() {
        state = "detonated";
    }

    void update(double deltaSeconds) {
        if ("pending".equals(state)) {
            releaseDelaySeconds -= deltaSeconds;
            if (releaseDelaySeconds > 0) {
                return;
            }
            deltaSeconds = Math.max(0, -releaseDelaySeconds);
            releaseDelaySeconds = 0;
            state = "falling";
        }
        if (!"falling".equals(state)) {
            return;
        }

        position = position.add(Vector2.fromHeading(heading).scale(horizontalSpeed * deltaSeconds));
        verticalSpeed += GRAVITY * deltaSeconds;
        altitude -= verticalSpeed * deltaSeconds;
        if (altitude <= SEA_LEVEL) {
            altitude = SEA_LEVEL;
            state = "detonated";
        }
    }

    BombSnapshot snapshot() {
        return new BombSnapshot(
                id,
                teamId,
                shipId,
                MathSupport.round(position.x()),
                MathSupport.round(altitude),
                MathSupport.round(position.z()),
                MathSupport.round(heading),
                MathSupport.round(horizontalSpeed),
                state,
                MathSupport.round(droppedAtSeconds)
        );
    }
}
