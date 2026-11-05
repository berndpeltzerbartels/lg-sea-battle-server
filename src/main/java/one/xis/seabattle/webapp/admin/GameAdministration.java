package one.xis.seabattle.webapp.admin;

import java.util.List;

/** Global game controls; exposed through the ADMIN page. */
public interface GameAdministration {
    List<SetupOption> setups();
    String currentSetup();
    void restart();
    void selectSetup(String setupId);

    default String currentMode() { return "ENDLESS"; }
    default void start(String setup, String mode) { selectSetup(setup); }

    record SetupOption(String id, String title) { }
}
