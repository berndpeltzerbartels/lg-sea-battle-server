package one.xis.seabattle;

final class Ship {

    private static final double MAX_ACCEPTED_PLAYER_POSITION_DELTA = 24;

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
    private int torpedoesRemaining = 12;
    private double nextFireTime;
    private double respawnAtSeconds = Double.POSITIVE_INFINITY;

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

    Vector2 position() {
        return position;
    }

    double heading() {
        return heading;
    }

    double speed() {
        return speed;
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

    void applyPlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        if (!"active".equals(state)) {
            return;
        }
        Vector2 requestedPosition = new Vector2(update.x(), update.z());
        if (position.distanceTo(requestedPosition) > MAX_ACCEPTED_PLAYER_POSITION_DELTA
                || navigationService.isShipBlocked(requestedPosition, update.heading(), worldMap)) {
            applyCommand(update.engineOrder(), update.rudderDegrees());
            return;
        }

        position = requestedPosition;
        heading = MathSupport.normalizeAngle(update.heading());
        speed = update.speed();
        engineOrder = MathSupport.clamp(update.engineOrder(), 0, 8);
        rudderDegrees = MathSupport.clamp(update.rudderDegrees(), -35, 35);
    }

    void update(double deltaSeconds, NavigationService navigationService, WorldMap worldMap) {
        if (!"active".equals(state)) {
            return;
        }

        Vector2 previousPosition = position;
        double previousSpeed = speed;
        double targetSpeed = EngineOrders.speedFor(engineOrder);
        double speedResponse = Math.abs(targetSpeed) > Math.abs(speed) ? 0.45 : 0.75;
        speed += (targetSpeed - speed) * Math.min(1, deltaSeconds * speedResponse);

        double rudderRatio = rudderDegrees / 35.0;
        double turnStrength = speed >= 0 ? 0.24 : -0.16;
        double rudderGrip = MathSupport.clamp(Math.abs(speed) / 4.2, 0, 1);
        double targetTurnVelocity = rudderRatio * turnStrength * rudderGrip;
        turnVelocity += (targetTurnVelocity - turnVelocity) * Math.min(1, deltaSeconds * 2.0);
        heading = MathSupport.normalizeAngle(heading + turnVelocity * deltaSeconds);

        position = position.add(Vector2.fromHeading(heading).scale(speed * deltaSeconds));
        if (navigationService.isShipBlocked(position, heading, worldMap)) {
            position = previousPosition;
            speed = Math.min(0, previousSpeed * 0.15);
            turnVelocity *= 0.35;
        }
    }

    boolean canFire(double nowSeconds) {
        return "active".equals(state) && torpedoesRemaining > 0 && nowSeconds >= nextFireTime;
    }

    void markFired(double nowSeconds, double cooldownSeconds) {
        nextFireTime = nowSeconds + cooldownSeconds;
    }

    void stopAfterRamImpact() {
        if (!"active".equals(state)) {
            return;
        }
        speed = 0;
        turnVelocity = 0;
        engineOrder = 2;
        rudderDegrees = 0;
    }

    boolean sink(double respawnAtSeconds) {
        if (!"active".equals(state)) {
            return false;
        }
        state = "sunk";
        speed = 0;
        turnVelocity = 0;
        rudderDegrees = 0;
        engineOrder = 2;
        controlledBy = "bot";
        this.respawnAtSeconds = respawnAtSeconds;
        return true;
    }

    boolean isReadyToRespawn(double nowSeconds) {
        return "sunk".equals(state) && nowSeconds >= respawnAtSeconds;
    }

    boolean isVisibleInSnapshot(double nowSeconds) {
        return "active".equals(state) || ("sunk".equals(state) && nowSeconds < respawnAtSeconds);
    }

    void respawn(Vector2 position, double heading, double nowSeconds) {
        this.position = position;
        this.heading = MathSupport.normalizeAngle(heading);
        speed = 0;
        turnVelocity = 0;
        engineOrder = 2;
        rudderDegrees = 0;
        controlledBy = "bot";
        state = "active";
        torpedoesRemaining = 12;
        nextFireTime = nowSeconds + 3;
        respawnAtSeconds = Double.POSITIVE_INFINITY;
    }

    ShipSnapshot snapshot() {
        return new ShipSnapshot(
                id,
                teamId,
                MathSupport.round(position.x()),
                MathSupport.round(position.z()),
                MathSupport.round(heading),
                MathSupport.round(speed),
                MathSupport.round(turnVelocity),
                rudderDegrees,
                engineOrder,
                state,
                controlledBy,
                torpedoesRemaining
        );
    }
}
