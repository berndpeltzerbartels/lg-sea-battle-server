package one.xis.seabattle;

final class MathSupport {

    private MathSupport() {
    }

    static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    static double normalizeAngle(double angle) {
        double normalized = angle;
        while (normalized > Math.PI) {
            normalized -= Math.PI * 2;
        }
        while (normalized < -Math.PI) {
            normalized += Math.PI * 2;
        }
        return normalized;
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
