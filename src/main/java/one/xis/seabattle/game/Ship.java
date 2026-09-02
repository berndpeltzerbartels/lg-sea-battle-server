package one.xis.seabattle.game;

final class Ship {

    private static final double MAX_ACCEPTED_PLAYER_POSITION_DELTA = 90;
    private static final double MAX_ACCEPTED_PLAYER_SPEED = 48;
    private static final double SCOUT_PLANE_ALTITUDE_SCALE = SeaBattleGameConfig.SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double BOT_SCOUT_PLANE_ALTITUDE_RESPONSE = 0.38;
    private static final double SCOUT_PLANE_MIN_Y = 3 * SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double SCOUT_PLANE_MAX_Y = 200 * SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double BOT_SCOUT_PLANE_Y = 170;
    private static final double BOT_SCOUT_PLANE_SPEED = 25.65;
    private static final double MAX_ACCEPTED_PLAYER_TURN_VELOCITY = 1.2;
    private static final int ENGINE_FULL_ASTERN = 0;
    private static final int TORPEDO_STOCK = 12;
    private static final String VEHICLE_TORPEDO_BOAT = "torpedo-boat";
    private static final String VEHICLE_SUBMARINE = "submarine";
    private static final String VEHICLE_SCOUT_PLANE = "scout-plane";
    private static final String DEPTH_SURFACE = "surface";
    private static final String DEPTH_PERISCOPE = "periscope";
    private static final String DEPTH_SUBMERGED = "submerged";

    private final String id;
    private final String teamId;
    private Vector2 position;
    private double y;
    private double verticalSpeed;
    private double heading;
    private double speed;
    private double turnVelocity;
    private int engineOrder = 2;
    private int rudderDegrees;
    private String controlledBy;
    private String vehicleType = VEHICLE_TORPEDO_BOAT;
    private String state = "active";
    private int torpedoesRemaining = TORPEDO_STOCK;
    private double nextFireTime;
    private double respawnAtSeconds = Double.POSITIVE_INFINITY;
    private double glancingRamBackoffUntilSeconds = Double.NEGATIVE_INFINITY;
    private Vector2 glancingRamBackoffTarget;
    private double botScoutPlaneTargetY = BOT_SCOUT_PLANE_Y;
    private double flakYaw;
    private double flakPitch;
    private double cannonYaw;
    private double cannonPitch;
    private String depthState = DEPTH_SURFACE;

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

    String vehicleType() {
        return vehicleType;
    }

    boolean isScoutPlane() {
        return VEHICLE_SCOUT_PLANE.equals(vehicleType);
    }

    boolean isSubmarine() {
        return VEHICLE_SUBMARINE.equals(vehicleType);
    }

    boolean isFullySubmerged() {
        return isSubmarine() && DEPTH_SUBMERGED.equals(depthState);
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

    void vehicleType(String vehicleType) {
        this.vehicleType = normalizeVehicleType(vehicleType);
        if (isScoutPlane()) {
            y = BOT_SCOUT_PLANE_Y;
            botScoutPlaneTargetY = BOT_SCOUT_PLANE_Y;
            speed = Math.max(speed, BOT_SCOUT_PLANE_SPEED);
            engineOrder = 7;
            depthState = DEPTH_SURFACE;
        } else if (!isSubmarine()) {
            depthState = DEPTH_SURFACE;
        }
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

    double y() {
        return y;
    }

    double verticalSpeed() {
        return verticalSpeed;
    }

    double turnVelocity() {
        return turnVelocity;
    }

    void flakAim(double yaw, double pitch) {
        flakYaw = MathSupport.normalizeAngle(yaw);
        flakPitch = pitch;
    }

    void cannonAim(double yaw, double pitch) {
        cannonYaw = MathSupport.normalizeAngle(yaw);
        cannonPitch = pitch;
    }

    void nextFireTime(double nextFireTime) {
        this.nextFireTime = nextFireTime;
    }

    void y(double y) {
        this.y = isScoutPlane() ? MathSupport.clamp(y, SCOUT_PLANE_MIN_Y, SCOUT_PLANE_MAX_Y) : 0;
        if (isScoutPlane()) {
            botScoutPlaneTargetY = this.y;
        }
    }

    void botScoutPlaneTargetY(double y) {
        if (isScoutPlane()) {
            botScoutPlaneTargetY = MathSupport.clamp(y, SCOUT_PLANE_MIN_Y, SCOUT_PLANE_MAX_Y);
        }
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
        vehicleType = normalizeVehicleType(update.vehicleType());
        depthState = isSubmarine() ? normalizeDepthState(update.depthState()) : DEPTH_SURFACE;
        boolean implausiblePosition = position.distanceTo(requestedPosition) > MAX_ACCEPTED_PLAYER_POSITION_DELTA;
        boolean blockedPosition = !isScoutPlane() && navigationService.isShipBlocked(requestedPosition, update.heading(), worldMap);
        if ((!update.debugTeleport() && implausiblePosition) || blockedPosition) {
            applyCommand(update.engineOrder(), update.rudderDegrees());
            return;
        }

        position = requestedPosition;
        y = isScoutPlane() ? MathSupport.clamp(update.y(), SCOUT_PLANE_MIN_Y, SCOUT_PLANE_MAX_Y) : 0;
        verticalSpeed = isScoutPlane() ? MathSupport.clamp(update.verticalSpeed(), -34, 20) : 0;
        heading = MathSupport.normalizeAngle(update.heading());
        speed = MathSupport.clamp(update.speed(), -MAX_ACCEPTED_PLAYER_SPEED, MAX_ACCEPTED_PLAYER_SPEED);
        turnVelocity = MathSupport.clamp(
                update.turnVelocity(),
                -MAX_ACCEPTED_PLAYER_TURN_VELOCITY,
                MAX_ACCEPTED_PLAYER_TURN_VELOCITY
        );
        engineOrder = MathSupport.clamp(update.engineOrder(), 0, 8);
        rudderDegrees = MathSupport.clamp(update.rudderDegrees(), -35, 35);
        if (isFinite(update.flakYaw()) && isFinite(update.flakPitch())) {
            flakAim(update.flakYaw(), update.flakPitch());
        }
        if (isFinite(update.cannonYaw()) && isFinite(update.cannonPitch())) {
            cannonAim(update.cannonYaw(), update.cannonPitch());
        }
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    void applyScoutPlaneWeaponState(Vector2 position, double y, double heading, double speed, double verticalSpeed) {
        if (!"active".equals(state) || !isScoutPlane()) {
            return;
        }
        this.position = position;
        this.y = MathSupport.clamp(y, SCOUT_PLANE_MIN_Y, SCOUT_PLANE_MAX_Y);
        this.heading = MathSupport.normalizeAngle(heading);
        this.speed = MathSupport.clamp(speed, -MAX_ACCEPTED_PLAYER_SPEED, MAX_ACCEPTED_PLAYER_SPEED);
        this.verticalSpeed = MathSupport.clamp(verticalSpeed, -34, 20);
    }

    void update(double deltaSeconds, NavigationService navigationService, WorldMap worldMap) {
        if (!"active".equals(state)) {
            return;
        }
        if (isScoutPlane()) {
            updateScoutPlane(deltaSeconds);
            return;
        }

        Vector2 previousPosition = position;
        double previousSpeed = speed;
        double targetSpeed = EngineOrders.speedFor(engineOrder);
        double speedResponse = Math.abs(targetSpeed) > Math.abs(speed) ? 0.45 : 0.42;
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

    private void updateScoutPlane(double deltaSeconds) {
        double targetSpeed = BOT_SCOUT_PLANE_SPEED;
        speed += (targetSpeed - speed) * Math.min(1, deltaSeconds * 0.55);

        double rudderRatio = rudderDegrees / 35.0;
        double targetTurnVelocity = rudderRatio * 0.18;
        turnVelocity += (targetTurnVelocity - turnVelocity) * Math.min(1, deltaSeconds * 1.2);
        heading = MathSupport.normalizeAngle(heading + turnVelocity * deltaSeconds);
        position = position.add(Vector2.fromHeading(heading).scale(speed * deltaSeconds));
        y += (botScoutPlaneTargetY - y) * Math.min(1, deltaSeconds * BOT_SCOUT_PLANE_ALTITUDE_RESPONSE);
        verticalSpeed = 0;
        engineOrder = 7;
    }

    boolean canFire(double nowSeconds) {
        return "active".equals(state) && !isScoutPlane() && nowSeconds >= nextFireTime;
    }

    boolean canDropBomb(double nowSeconds) {
        return "active".equals(state) && isScoutPlane() && nowSeconds >= nextFireTime;
    }

    void markFired(double nowSeconds, double cooldownSeconds) {
        nextFireTime = nowSeconds + cooldownSeconds;
        torpedoesRemaining = TORPEDO_STOCK;
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
        speed = isScoutPlane() ? BOT_SCOUT_PLANE_SPEED : 0;
        turnVelocity = 0;
        engineOrder = isScoutPlane() ? 7 : 2;
        rudderDegrees = 0;
        controlledBy = "bot";
        y = isScoutPlane() ? BOT_SCOUT_PLANE_Y : 0;
        botScoutPlaneTargetY = isScoutPlane() ? BOT_SCOUT_PLANE_Y : 0;
        verticalSpeed = 0;
        depthState = DEPTH_SURFACE;
        state = "active";
        torpedoesRemaining = TORPEDO_STOCK;
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
                torpedoesRemaining,
                vehicleType,
                MathSupport.round(isScoutPlane() ? y : 0),
                MathSupport.round(flakYaw),
                MathSupport.round(flakPitch),
                MathSupport.round(cannonYaw),
                MathSupport.round(cannonPitch),
                isSubmarine() ? depthState : DEPTH_SURFACE
        );
    }

    private static String normalizeVehicleType(String vehicleType) {
        if (VEHICLE_SCOUT_PLANE.equals(vehicleType)) {
            return VEHICLE_SCOUT_PLANE;
        }
        if (VEHICLE_SUBMARINE.equals(vehicleType)) {
            return VEHICLE_SUBMARINE;
        }
        return VEHICLE_TORPEDO_BOAT;
    }

    private static String normalizeDepthState(String depthState) {
        if (DEPTH_PERISCOPE.equals(depthState)) {
            return DEPTH_PERISCOPE;
        }
        if (DEPTH_SUBMERGED.equals(depthState)) {
            return DEPTH_SUBMERGED;
        }
        return DEPTH_SURFACE;
    }
}
