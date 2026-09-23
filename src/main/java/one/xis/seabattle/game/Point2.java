package one.xis.seabattle.game;

record Point2(double x, double z, String boundaryPointId) {
    Point2(double x, double z) {
        this(x, z, null);
    }
}
