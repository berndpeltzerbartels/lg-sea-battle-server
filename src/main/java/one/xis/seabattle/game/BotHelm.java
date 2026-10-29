package one.xis.seabattle.game;

/** Cheap feedback steering: planning supplies a heading, never a persistent rudder position. */
final class BotHelm {
    private BotHelm() {}

    static int rudder(double heading, double turnVelocity, double speed, double desiredHeading) {
        double error = MathSupport.normalizeAngle(desiredHeading - heading);
        // Start straightening before reaching the course; the hull still has angular momentum.
        double correction = error - turnVelocity * .55;
        double direction = speed < -.05 ? -1 : 1;
        return (int) Math.round(MathSupport.clamp(correction / .58, -1, 1) * 35 * direction);
    }
}
