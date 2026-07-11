package one.xis.seabattle;

final class Ship {

    private static final double MAX_ACCEPTED_PLAYER_POSITION_DELTA = 90;
    private static final double MAX_ACCEPTED_PLAYER_SPEED = 16;
    private static final double MAX_ACCEPTED_PLAYER_TURN_VELOCITY = 1.2;
    private static final int ENGINE_FULL_ASTERN = 0;

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
    private double glancingRamBackoffUntilSeconds = Double.NEGATIVE_INFINITY;
    private Vector2 glancingRamBackoffTarget;

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

    boolean isBotControlled() {
        return "bot".equals(controlledBy);
    }

    boolean isServerSimulated() {
        return isBotControlled() || "scenario".equals(controlledBy);
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

    boolean applyGlancingRamBackoff(double nowSeconds) {
        if (!"active".equals(state) || nowSeconds >= glancingRamBackoffUntilSeconds) {
            glancingRamBackoffTarget = null;
            return false;
        }
        applyCommand(ENGINE_FULL_ASTERN, glancingRamBackoffRudder());
        return true;
    }

    void applyPlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        if (!"active".equals(state)) {
            return;
        }
        Vector2 requestedPosition = new Vector2(update.x(), update.z());
        boolean implausiblePosition = position.distanceTo(requestedPosition) > MAX_ACCEPTED_PLAYER_POSITION_DELTA;
        boolean blockedPosition = navigationService.isShipBlocked(requestedPosition, update.heading(), worldMap);
        if ((!update.debugTeleport() && implausiblePosition) || blockedPosition) {
            applyCommand(update.engineOrder(), update.rudderDegrees());
            return;
        }

        position = requestedPosition;
        heading = MathSupport.normalizeAngle(update.heading());
        speed = MathSupport.clamp(update.speed(), -MAX_ACCEPTED_PLAYER_SPEED, MAX_ACCEPTED_PLAYER_SPEED);
        turnVelocity = MathSupport.clamp(
                update.turnVelocity(),
                -MAX_ACCEPTED_PLAYER_TURN_VELOCITY,
                MAX_ACCEPTED_PLAYER_TURN_VELOCITY
        );
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
        if (navigationService.isShipBlocked(position, heading, worldMap)
                && navigationService.isShipMovementBlocked(position, heading, speed, worldMap)) {
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
        torpedoesRemaining = Math.max(0, torpedoesRemaining - 1);
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

    void glanceOff(double headingImpulse, double speedFactor) {
        if (!"active".equals(state)) {
            return;
        }
        heading = MathSupport.normalizeAngle(heading + headingImpulse);
        speed *= MathSupport.clamp(speedFactor, 0, 1);
        turnVelocity *= 0.25;
        engineOrder = Math.min(engineOrder, 4);
        rudderDegrees = 0;
    }

    void backOffAfterGlancingRam(double nowSeconds, double durationSeconds, Vector2 targetPosition) {
        if (!"active".equals(state)) {
            return;
        }
        boolean alreadyBackingOff = nowSeconds < glancingRamBackoffUntilSeconds;
        glancingRamBackoffUntilSeconds = Math.max(glancingRamBackoffUntilSeconds, nowSeconds + durationSeconds);
        glancingRamBackoffTarget = targetPosition;
        if (!alreadyBackingOff) {
            speed = Math.min(0, speed * 0.15);
            turnVelocity *= 0.25;
        }
        engineOrder = ENGINE_FULL_ASTERN;
        rudderDegrees = glancingRamBackoffRudder();
    }

    private int glancingRamBackoffRudder() {
        if (glancingRamBackoffTarget == null) {
            return 0;
        }
        double desiredHeading = Math.atan2(
                glancingRamBackoffTarget.x() - position.x(),
                glancingRamBackoffTarget.z() - position.z()
        );
        double targetBearing = MathSupport.normalizeAngle(desiredHeading - heading);
        return (int) Math.round(MathSupport.clamp(-targetBearing / 0.58, -1, 1) * 35);
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
        glancingRamBackoffUntilSeconds = Double.NEGATIVE_INFINITY;
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
        glancingRamBackoffUntilSeconds = Double.NEGATIVE_INFINITY;
        glancingRamBackoffTarget = null;
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
