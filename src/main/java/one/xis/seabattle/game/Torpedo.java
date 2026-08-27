package one.xis.seabattle.game;

final class Torpedo {

    private static final double GRAVITY = 14.0;
    private static final double WATER_Y = 0.05;

    private final String id;
    private final String teamId;
    private final String shipId;
    private Vector2 position;
    private Vector2 previousPosition;
    private final double heading;
    private final double speed;
    private final double airborneSpeed;
    private final double firedAtSeconds;
    private final double maxRange;
    private final int tubeSide;
    private double y;
    private double previousY;
    private double verticalSpeed;
    private double runDistance;
    private String state = "running";

    Torpedo(String id, String teamId, String shipId, Vector2 position, double heading, double speed, double firedAtSeconds, double maxRange) {
        this(id, teamId, shipId, position, heading, speed, firedAtSeconds, maxRange, 0);
    }

    Torpedo(String id, String teamId, String shipId, Vector2 position, double heading, double speed, double firedAtSeconds, double maxRange, int tubeSide) {
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
        this.position = position;
        this.previousPosition = position;
        this.heading = heading;
        this.speed = speed;
        this.airborneSpeed = speed;
        this.firedAtSeconds = firedAtSeconds;
        this.maxRange = maxRange;
        this.tubeSide = normalizeTubeSide(tubeSide);
        this.y = WATER_Y;
        this.previousY = WATER_Y;
        this.verticalSpeed = 0;
    }

    static Torpedo airDropped(
            String id,
            String teamId,
            String shipId,
            Vector2 position,
            double heading,
            double airborneSpeed,
            double waterSpeed,
            double y,
            double verticalSpeed,
            double firedAtSeconds,
            double maxRange
    ) {
        return new Torpedo(id, teamId, shipId, position, heading, airborneSpeed, waterSpeed, y, verticalSpeed, firedAtSeconds, maxRange);
    }

    private Torpedo(
            String id,
            String teamId,
            String shipId,
            Vector2 position,
            double heading,
            double airborneSpeed,
            double waterSpeed,
            double y,
            double verticalSpeed,
            double firedAtSeconds,
            double maxRange
    ) {
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
        this.position = position;
        this.previousPosition = position;
        this.heading = heading;
        this.speed = waterSpeed;
        this.airborneSpeed = airborneSpeed;
        this.firedAtSeconds = firedAtSeconds;
        this.maxRange = maxRange;
        this.tubeSide = 0;
        this.y = Math.max(WATER_Y, y);
        this.previousY = this.y;
        this.verticalSpeed = verticalSpeed;
        this.state = "airborne";
    }

    String state() {
        return state;
    }

    String teamId() {
        return teamId;
    }

    String shipId() {
        return shipId;
    }

    String id() {
        return id;
    }

    Vector2 position() {
        return position;
    }

    Vector2 previousPosition() {
        return previousPosition;
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

    double previousY() {
        return previousY;
    }

    double verticalSpeed() {
        return verticalSpeed;
    }

    void hit() {
        state = "hit";
    }

    void update(double deltaSeconds) {
        if ("airborne".equals(state)) {
            updateAirborne(deltaSeconds);
            return;
        }
        if (!"running".equals(state)) {
            return;
        }

        double step = speed * deltaSeconds;
        previousPosition = position;
        position = position.add(Vector2.fromHeading(heading).scale(step));
        runDistance += step;
        if (runDistance >= maxRange) {
            state = "expired";
        }
    }

    private void updateAirborne(double deltaSeconds) {
        previousPosition = position;
        previousY = y;
        position = position.add(Vector2.fromHeading(heading).scale(airborneSpeed * deltaSeconds));
        y += verticalSpeed * deltaSeconds - 0.5 * GRAVITY * deltaSeconds * deltaSeconds;
        verticalSpeed -= GRAVITY * deltaSeconds;
        if (y <= WATER_Y) {
            y = WATER_Y;
            verticalSpeed = 0;
            state = "running";
            previousPosition = position;
        }
    }

    TorpedoSnapshot snapshot() {
        return new TorpedoSnapshot(
                id,
                teamId,
                shipId,
                MathSupport.round(position.x()),
                MathSupport.round(position.z()),
                MathSupport.round(heading),
                MathSupport.round("airborne".equals(state) ? airborneSpeed : speed),
                MathSupport.round(y),
                MathSupport.round(verticalSpeed),
                state,
                MathSupport.round(firedAtSeconds),
                tubeSide
        );
    }

    private int normalizeTubeSide(int value) {
        if (value < 0) {
            return -1;
        }
        if (value > 0) {
            return 1;
        }
        return 0;
    }
}
