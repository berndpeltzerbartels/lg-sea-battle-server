package one.xis.seabattle;

import one.xis.http.XISHttpApplication;
import one.xis.http.XISHttpRunner;

@XISHttpApplication
public class SeaBattleApplication {

    public static void main(String[] args) {
        XISHttpRunner.run(SeaBattleApplication.class, args);
    }
}
