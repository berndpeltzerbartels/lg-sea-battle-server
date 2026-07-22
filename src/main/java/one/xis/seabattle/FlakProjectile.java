package one.xis.seabattle;

final class FlakProjectile {

    private static final double GRAVITY = 9.0;
    private static final double LIFETIME_SECONDS = 8.0;

    private final String id;
    private final String teamId;
    private final String shipId;
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

    FlakProjectile(String id, String teamId, String shipId, double x, double y, double z,
                   double vx, double vy, double vz, double firedAtSeconds) {
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
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
    }

    String state() {
        return ageSeconds >= LIFETIME_SECONDS || y < 0 ? "expired" : "flying";
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
        ageSeconds = LIFETIME_SECONDS;
    }

    void update(double deltaSeconds) {
        if (!"flying".equals(state())) {
            return;
        }
        ageSeconds += deltaSeconds;
        previousX = x;
        previousY = y;
        previousZ = z;
        vy -= GRAVITY * deltaSeconds;
        x += vx * deltaSeconds;
        y += vy * deltaSeconds;
        z += vz * deltaSeconds;
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
                MathSupport.round(firedAtSeconds)
        );
    }
}
