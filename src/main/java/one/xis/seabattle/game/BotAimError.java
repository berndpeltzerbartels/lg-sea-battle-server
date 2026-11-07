package one.xis.seabattle.game;

/** One stable aiming estimate per target/life/shot, with no random work each tick. */
final class BotAimError {
    private Vehicle target;
    private long ownLife, targetLife, shot;
    private double unitError;

    double angle(SurfaceVehicle shooter, Vehicle nextTarget, double maximum) {
        if (maximum == 0) return 0;
        long nextShot = Double.doubleToLongBits(shooter.nextFireTime);
        if (target != nextTarget || ownLife != shooter.lifeId() || targetLife != nextTarget.lifeId() || shot != nextShot) {
            target = nextTarget;
            ownLife = shooter.lifeId();
            targetLife = nextTarget.lifeId();
            shot = nextShot;
            long value = ((long) shooter.id().hashCode() << 32) ^ target.id().hashCode()
                    ^ ownLife * 0x9e3779b97f4a7c15L ^ targetLife * 0x632be59bd9b4e019L ^ shot;
            value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
            value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
            value ^= value >>> 31;
            unitError = (value >>> 11) * 0x1.0p-53 * 2 - 1;
        }
        return unitError * maximum;
    }

    void reset() { target = null; }
}
