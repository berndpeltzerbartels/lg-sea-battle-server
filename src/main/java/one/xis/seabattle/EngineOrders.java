package one.xis.seabattle;

final class EngineOrders {

    private static final double[] SPEEDS = {-4.2, -2.2, 0, 0.55, 1.8, 3.8, 6.4, 9.6, 12.4};

    private EngineOrders() {
    }

    static double speedFor(int engineOrder) {
        if (engineOrder < 0 || engineOrder >= SPEEDS.length) {
            return 0;
        }
        return SPEEDS[engineOrder];
    }
}
