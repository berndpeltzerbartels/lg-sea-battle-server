package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmarineDiveMotionTest {
    @Test
    void deepStageTakesSixSecondsBothWays() {
        assertEquals(-12.42, Ship.advanceSubmarineDepth(-5.58, -19.26, 3), 1e-9);
        assertEquals(-19.26, Ship.advanceSubmarineDepth(-5.58, -19.26, 6), 1e-9);
        assertEquals(-5.58, Ship.advanceSubmarineDepth(-19.26, -5.58, 6), 1e-9);
        assertEquals(-5.58, Ship.advanceSubmarineDepth(-19.26, -5.58, 30), 1e-9);
    }

    @Test
    void upperStageKeepsSpeedAndBoundaryCrossingIsTickIndependent() {
        assertEquals(-1.62, Ship.advanceSubmarineDepth(-.78, -19.26, 1), 1e-9);
        assertEquals(-4.74, Ship.advanceSubmarineDepth(-5.58, -.78, 1), 1e-9);
        for (double[] path : new double[][]{{-.78, -19.26}, {-19.26, -.78}}) {
            double y = path[0];
            for (int i = 0; i < 90; i++) y = Ship.advanceSubmarineDepth(y, path[1], .1);
            assertEquals(Ship.advanceSubmarineDepth(path[0], path[1], 9), y, 1e-9);
        }
    }
}
