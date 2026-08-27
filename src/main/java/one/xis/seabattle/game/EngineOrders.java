package one.xis.seabattle.game;

final class EngineOrders {

    private static final double[] SPEEDS = {-8.0, -2.2, 0, 0.69, 2.25, 4.75, 8.0, 12.0, 15.5};

    private EngineOrders() {
    }

    static double speedFor(int engineOrder) {
        if (engineOrder < 0 || engineOrder >= SPEEDS.length) {
            return 0;
        }
        return SPEEDS[engineOrder];
    }

    static double[] speeds() {
        return SPEEDS.clone();
    }
}
