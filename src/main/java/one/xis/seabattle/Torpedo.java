package one.xis.seabattle;

final class Torpedo {

    private final String id;
    private final String teamId;
    private final String shipId;
    private Vector2 position;
    private Vector2 previousPosition;
    private final double heading;
    private final double speed;
    private final double firedAtSeconds;
    private final double maxRange;
    private double runDistance;
    private String state = "running";

    Torpedo(String id, String teamId, String shipId, Vector2 position, double heading, double speed, double firedAtSeconds, double maxRange) {
        this.id = id;
        this.teamId = teamId;
        this.shipId = shipId;
        this.position = position;
        this.previousPosition = position;
        this.heading = heading;
        this.speed = speed;
        this.firedAtSeconds = firedAtSeconds;
        this.maxRange = maxRange;
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

    void hit() {
        state = "hit";
    }

    void update(double deltaSeconds) {
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

    TorpedoSnapshot snapshot() {
        return new TorpedoSnapshot(
                id,
                teamId,
                shipId,
                MathSupport.round(position.x()),
                MathSupport.round(position.z()),
                MathSupport.round(heading),
                MathSupport.round(speed),
                state,
                MathSupport.round(firedAtSeconds)
        );
    }
}
