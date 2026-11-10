package one.xis.seabattle.webapp;

import one.xis.seabattle.webapp.controller.ControllerStartDraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerSetupNavigationTest {
    @Test void openingSetupNeedsNoValidLoginOrGameServicesAndReturningRestoresTheDraft() {
        // No game or account dependencies: configuration must never join a fleet or start a game.
        var page = new SeaBattleLoginPage(null, null, null, null, null, null);
        var draft = new ControllerStartDraft("draft", "", "", "dark", "", "submarine");
        var response = page.configureController(draft);
        assertEquals("/controller.html", response.getUrl());
        assertEquals(draft, response.getSessionStorage().get("controllerStartDraft"));
        var restored = page.login(null, "torpedo-boat", draft);
        assertEquals("submarine", restored.vehicleType());assertEquals("dark", restored.team());
        assertEquals("", restored.nickname());assertEquals("draft", restored.id());
    }
}
