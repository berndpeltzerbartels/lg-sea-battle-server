package one.xis.seabattle.game;

/** Cheap feedback steering: planning supplies a heading, never a persistent rudder position. */
final class BotHelm {
    private BotHelm() {}

    /** More precise tracking only on an unobstructed attack course; safety still wins. */
    static int aimRudder(double heading, double turnVelocity, double speed, BotTorpedoAim aim) {
        return aimRudder(heading, turnVelocity, speed, aim, .40, .45, .0015);
    }

    static int aimRudder(double heading, double turnVelocity, double speed, BotTorpedoAim aim,
                         one.xis.seabattle.webapp.gameconfig.TorpedoBoatBotConfig config) {
        return aimRudder(heading, turnVelocity, speed, aim, config.aimResponse(),
                config.aimTurnDamping(), config.aimMinCorrectionAngle());
    }

    private static int aimRudder(double heading, double turnVelocity, double speed, BotTorpedoAim aim,
                                 double response, double damping, double minCorrectionAngle) {
        double error = MathSupport.normalizeAngle(aim.heading() - heading);
        double turnLimit = (speed >= 0 ? .24 : -.16)
                * MathSupport.clamp(Math.abs(speed) / 4.2, 0, 1);
        if (Math.abs(turnLimit) < .005) return rudder(heading, turnVelocity, speed, aim.heading());
        double command = aim.angularVelocity() / turnLimit
                + (error / response + (aim.angularVelocity() - turnVelocity) * damping)
                    * (speed >= 0 ? 1 : -1);
        int rudder = (int) Math.round(MathSupport.clamp(command, -1, 1) * 35);
        if (rudder == 0 && Math.abs(error) > minCorrectionAngle)
            rudder = (error * turnLimit > 0 ? 1 : -1);
        return rudder;
    }

    static int rudder(double heading, double turnVelocity, double speed, double desiredHeading) {
        double error = MathSupport.normalizeAngle(desiredHeading - heading);
        // Start straightening before reaching the course; the hull still has angular momentum.
        double correction = error - turnVelocity * .55;
        double direction = speed < -.05 ? -1 : 1;
        return (int) Math.round(MathSupport.clamp(correction / .58, -1, 1) * 35 * direction);
    }
}
