package one.xis.seabattle;

final class EngineOrders {

    private static final double[] SPEEDS = {-4.2, -2.2, 0, 1.6, 3.2, 5.2, 7.2, 9.6, 12.4};

    private EngineOrders() {
    }

    static double speedFor(int engineOrder) {
        if (engineOrder < 0 || engineOrder >= SPEEDS.length) {
            return 0;
        }
        return SPEEDS[engineOrder];
    }
}
