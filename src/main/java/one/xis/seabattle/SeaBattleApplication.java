package one.xis.seabattle;

import one.xis.boot.XISBootApplication;
import one.xis.boot.XISBootRunner;

@XISBootApplication
public class SeaBattleApplication {

    public static void main(String[] args) {
        // Keep the same 40-vehicle fleet for IDE, Gradle and production starts.
        if (System.getProperty("seaBattle.localFleetTrial") == null) {
            System.setProperty("seaBattle.localFleetTrial", "true");
        }
        XISBootRunner.run(SeaBattleApplication.class, args);
    }
}
