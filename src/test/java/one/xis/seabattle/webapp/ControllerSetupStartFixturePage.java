package one.xis.seabattle.webapp;

import one.xis.*;
import one.xis.seabattle.webapp.controller.ControllerStartDraft;

/** Fixture for the native XIS navigation contract, using the real login model/action. */
@Page("/start.html")
@HtmlFile("/ControllerSetupStartFixturePage.html")
public class ControllerSetupStartFixturePage {
    private final SeaBattleLoginPage login = new SeaBattleLoginPage(null, null, null, null, null, null);
    @FormData("login")
    SeaBattleLoginPage.LoginForm form(@NullAllowed @SessionStorage("controllerStartDraft") ControllerStartDraft draft) {
        return draft == null ? new SeaBattleLoginPage.LoginForm("draft", "", "", "light", "", "torpedo-boat")
                : login.login(null, null, draft);
    }
    @Action("configureController")
    PageUrlResponse configure(@FormData("login") ControllerStartDraft draft) { return login.configureController(draft); }
}
