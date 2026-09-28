package one.xis.seabattle.game;

final class FlakProjectile {

    private static final double FLAK_GRAVITY = 9.0;
    private static final double CANNON_GRAVITY = 9.8;
    private static final double FLAK_LIFETIME_SECONDS = 8.0;
    private static final double CANNON_LIFETIME_SECONDS = 18.0;

    private final String id;
    private final String teamId;
    private final String shipId;
    private final String shooterPlayerId;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final double initialVx;
    private final double initialVy;
    private final double initialVz;
    private double previousX;
    private double previousY;
    private double previousZ;
    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double vz;
    private final double firedAtSeconds;
    private double ageSeconds;
    private boolean hitResolved;
    private boolean collisionSegment;
    private final double waterEntryAge;
    private final double waterEntryX;
    private final double waterEntryZ;
    private final double waterVy;

    FlakProjectile(String id, String teamId, String shipId, double x, double y, double z,
                   double vx, double vy, double vz, double firedAtSeconds) {
        this(id, teamId, shipId, x, y, z, vx, vy, vz, firedAtSeconds, null);
    }

    FlakProjectile(String id, String teamId, String shipId, double x, double y, double z,
                   double vx, double vy, double vz, double firedAtSeconds, String shooterPlayerId) {
        this.shooterPlayerId = shooterPlayerId;
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.initialVx = vx;
        this.initialVy = vy;
        this.initialVz = vz;
        this.previousX = x;
        this.previousY = y;
        this.previousZ = z;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.firedAtSeconds = firedAtSeconds;
        waterEntryAge = y < 0 ? 0 : (vy + Math.sqrt(vy * vy + 2 * gravity() * y)) / gravity();
        waterEntryX = x + vx * waterEntryAge;
        waterEntryZ = z + vz * waterEntryAge;
        waterVy = vy - gravity() * waterEntryAge;
    }

    String state() {
        return ageSeconds >= lifetimeSeconds() ? "expired" : "flying";
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

    double previousX() {
        return previousX;
    }

    double previousY() {
        return previousY;
    }

    double previousZ() {
        return previousZ;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    void hit() {
        hitResolved = true;
        ageSeconds = lifetimeSeconds();
    }

    boolean hitResolved() {
        return hitResolved;
    }

    boolean hasCollisionSegment() {
        return collisionSegment;
    }

    void update(double deltaSeconds) {
        collisionSegment = false;
        if (!"flying".equals(state())) {
            return;
        }
        ageSeconds += deltaSeconds;
        previousX = x;
        previousY = y;
        previousZ = z;
        double sampleAge = Math.min(ageSeconds, lifetimeSeconds());
        collisionSegment = deltaSeconds > 0;
        x = originX + initialVx * sampleAge;
        y = originY + initialVy * sampleAge - 0.5 * gravity() * sampleAge * sampleAge;
        z = originZ + initialVz * sampleAge;
        vx = initialVx;
        vy = initialVy - gravity() * sampleAge;
        vz = initialVz;
        if (sampleAge >= waterEntryAge) {
            // Continue along the entry velocity until impact or the normal lifetime expires.
            double elapsed = sampleAge - waterEntryAge;
            x = waterEntryX + initialVx * elapsed;
            y = Math.min(0, originY) + waterVy * elapsed;
            z = waterEntryZ + initialVz * elapsed;
            vy = waterVy;
        }
    }

    private double gravity() {
        return id.startsWith("cannon-") ? CANNON_GRAVITY : FLAK_GRAVITY;
    }

    private double lifetimeSeconds() {
        return id.startsWith("cannon-") ? CANNON_LIFETIME_SECONDS : FLAK_LIFETIME_SECONDS;
    }

    FlakProjectileSnapshot snapshot() {
        return new FlakProjectileSnapshot(
                id,
                teamId,
                shipId,
                MathSupport.round(x),
                MathSupport.round(y),
                MathSupport.round(z),
                MathSupport.round(vx),
                MathSupport.round(vy),
                MathSupport.round(vz),
                MathSupport.round(firedAtSeconds),
                shooterPlayerId
        );
    }
}
