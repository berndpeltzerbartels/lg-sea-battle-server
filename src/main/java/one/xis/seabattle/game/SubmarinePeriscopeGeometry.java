package one.xis.seabattle.game;

/** Dimensions shared with the visible cylinders in the client submarine model. */
final class SubmarinePeriscopeGeometry {
    static final double SIZE_SCALE = 1.3; // Match the local client mast trial.
    static final double RADIUS = .019 * SIZE_SCALE;
    static final double BASE = .66 + .64 * .9 - .012;
    static final double TOP = BASE + .986 * .9 * SIZE_SCALE;
    static final double EXTRA_LIFT = (BASE + .986 * .9 - 1.86) * .2 * SIZE_SCALE;
    private static final double SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;

    static double lift(double rootY) {
        double depth = Math.abs(rootY / SCALE + .26);
        return EXTRA_LIFT * smooth(1.2, 1.6, depth) * (1 - smooth(1.6, 1.9, depth));
    }
    static double topAboveRoot(double rootY) { return (TOP + lift(rootY)) * SCALE; }
    private static double smooth(double a, double b, double x) {
        double t = MathSupport.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // Exact segment/cylinder intersection catches a thin mast even between projectile samples.
    static double hitParameter(Ship ship, double ax, double ay, double az, double bx, double by, double bz) {
        double sin = Math.sin(ship.heading()), cos = Math.cos(ship.heading());
        double x = ((ax - ship.position().x()) * cos - (az - ship.position().z()) * sin) / SCALE;
        double z = ((ax - ship.position().x()) * sin + (az - ship.position().z()) * cos) / SCALE;
        double dx = ((bx - ax) * cos - (bz - az) * sin) / SCALE;
        double dz = ((bx - ax) * sin + (bz - az) * cos) / SCALE;
        double y = (ay - ship.y()) / SCALE, dy = (by - ay) / SCALE;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 2; i++) {
            double centerZ = .04 + ((i == 0 ? .02 : .2) - .04) * .9;
            double top = BASE + (i == 0 ? .986 : .816) * .9 * SIZE_SCALE + lift(ship.y());
            double low = 0, high = 1;
            if (Math.abs(dy) < 1e-12) {
                if (y < BASE || y > top) continue;
            } else {
                double t0 = (BASE - y) / dy, t1 = (top - y) / dy;
                low = Math.max(low, Math.min(t0, t1)); high = Math.min(high, Math.max(t0, t1));
            }
            double a = dx * dx + dz * dz;
            double b = 2 * (x * dx + (z - centerZ) * dz);
            double c = x * x + (z - centerZ) * (z - centerZ) - RADIUS * RADIUS;
            if (a < 1e-12) { if (c > 0) continue; }
            else {
                double discriminant = b * b - 4 * a * c;
                if (discriminant < 0) continue;
                double root = Math.sqrt(discriminant);
                low = Math.max(low, (-b - root) / (2 * a));
                high = Math.min(high, (-b + root) / (2 * a));
            }
            if (low <= high) best = Math.min(best, low);
        }
        return best;
    }
}
