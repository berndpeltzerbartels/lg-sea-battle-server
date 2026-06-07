package one.xis.seabattle;

final class Ship {

    private final String id;
    private final String teamId;
    private Vector2 position;
    private double heading;
    private double speed;
    private double turnVelocity;
    private int engineOrder = 2;
    private int rudderDegrees;
    private String controlledBy;
    private String state = "active";
    private int torpedoesRemaining = 6;
    private double nextFireTime;

    Ship(String id, String teamId, Vector2 position, double heading, String controlledBy) {
        this.id = id;
        this.teamId = teamId;
        this.position = position;
        this.heading = heading;
        this.controlledBy = controlledBy;
    }

    String id() {
        return id;
    }

    String teamId() {
        return teamId;
    }

    String state() {
        return state;
    }

    String controlledBy() {
        return controlledBy;
    }

    void controlledBy(String controlledBy) {
        this.controlledBy = controlledBy;
    }

    void nextFireTime(double nextFireTime) {
        this.nextFireTime = nextFireTime;
    }

    void applyCommand(int engineOrder, int rudderDegrees) {
        if (!"active".equals(state)) {
            return;
        }
        this.engineOrder = MathSupport.clamp(engineOrder, 0, 8);
        this.rudderDegrees = MathSupport.clamp(rudderDegrees, -35, 35);
    }

    void update(double deltaSeconds) {
        if (!"active".equals(state)) {
            return;
        }

        double targetSpeed = EngineOrders.speedFor(engineOrder);
        double speedResponse = Math.abs(targetSpeed) > Math.abs(speed) ? 0.45 : 0.75;
        speed += (targetSpeed - speed) * Math.min(1, deltaSeconds * speedResponse);

        double rudderRatio = rudderDegrees / 35.0;
        double turnStrength = speed >= 0 ? 0.34 : -0.22;
        double rudderGrip = MathSupport.clamp(0.15 + Math.abs(speed) / 5.2, 0.15, 1);
        double targetTurnVelocity = rudderRatio * turnStrength * rudderGrip;
        turnVelocity += (targetTurnVelocity - turnVelocity) * Math.min(1, deltaSeconds * 2.0);
        heading = MathSupport.normalizeAngle(heading + turnVelocity * deltaSeconds);

        position = position.add(Vector2.fromHeading(heading).scale(speed * deltaSeconds));
    }

    boolean canFire(double nowSeconds) {
        return "active".equals(state) && torpedoesRemaining > 0 && nowSeconds >= nextFireTime;
    }

    void markFired(double nowSeconds, double cooldownSeconds) {
        torpedoesRemaining -= 1;
        nextFireTime = nowSeconds + cooldownSeconds;
    }

    void sink() {
        state = "sunk";
        speed = 0;
        turnVelocity = 0;
        rudderDegrees = 0;
    }

    ShipSnapshot snapshot() {
        return new ShipSnapshot(
                id,
                teamId,
                MathSupport.round(position.x()),
                MathSupport.round(position.z()),
                MathSupport.round(heading),
                MathSupport.round(speed),
                rudderDegrees,
                engineOrder,
                state,
                controlledBy,
                torpedoesRemaining
        );
    }
}
