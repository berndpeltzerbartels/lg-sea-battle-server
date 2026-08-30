package one.xis.seabattle.game;

final class EngineOrders {

    private static final double[] SPEEDS = {-8.0, -2.2, 0, 0.9, 2.93, 6.18, 10.4, 12.0, 17.5};

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
