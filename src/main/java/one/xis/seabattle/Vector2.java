package one.xis.seabattle;

record Vector2(double x, double z) {

    Vector2 add(Vector2 vector) {
        return new Vector2(x + vector.x, z + vector.z);
    }

    Vector2 subtract(Vector2 vector) {
        return new Vector2(x - vector.x, z - vector.z);
    }

    Vector2 scale(double factor) {
        return new Vector2(x * factor, z * factor);
    }

    Vector2 normalized() {
        double length = Math.hypot(x, z);
        if (length <= 0.0001) {
            return new Vector2(0, 1);
        }
        return new Vector2(x / length, z / length);
    }

    double distanceTo(Vector2 vector) {
        return Math.hypot(x - vector.x, z - vector.z);
    }

    static Vector2 fromHeading(double heading) {
        return new Vector2(Math.sin(heading), Math.cos(heading));
    }
}
