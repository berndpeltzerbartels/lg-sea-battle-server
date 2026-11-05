package one.xis.seabattle.webapp.gameconfig;

import one.xis.auth.UserAccountImpl;
import one.xis.context.IsolatedAdminPageContext;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigParameterPageTest {
    private final TestAdministration game = new TestAdministration();

    private static class TestAdministration implements one.xis.seabattle.webapp.admin.GameAdministration {
        String selected = "dense-land";
        int restarts;
        String mode = "ENDLESS";
        public String currentMode() { return mode; }
        public void start(String setup, String mode) { this.selected = setup; this.mode = mode; }
        public java.util.List<SetupOption> setups() {
            return java.util.List.of(new SetupOption("dense-land", "Inselgruppe"), new SetupOption("two-ship-duel", "Zwei Schiffe"));
        }
        public String currentSetup() { return selected; }
        public void restart() { restarts++; }
        public void selectSetup(String id) { selected = id; }
    }

    private GameConfigServiceImpl service() throws Exception {
        return new GameConfigServiceImpl(new GameConfigRuntimeTest.MemoryRepository(GameConfigRuntimeTest.defaults(true)));
    }

    private IsolatedAdminPageContext context(GameConfigService service, String role) {
        UserAccountImpl user = null;
        if (role != null) {
            user = new UserAccountImpl();
            user.setUserId("page-test-user");
            user.setRoles(Set.of(role));
        }
        return new IsolatedAdminPageContext(service, GameConfigParameterPage.class, GameConfigFormValidator.class, user, game);
    }

    @Test void guestsAndNonAdminsAreRedirectedToLogin() throws Exception {
        for (String role : new String[] {null, "USER"}) {
            var client = context(service(), role).openPage("/admin/game-config.html");
            assertTrue(client.getWindow().location.href.contains("/login.html"));
            assertNull(client.getDocument().getElementById("config-form"));
        }
    }

    @Test void adminCanEditAllFieldsAndSaveTheWholeConfiguration() throws Exception {
        var service = service();
        var client = context(service, "ADMIN").openPage("/admin/game-config.html");
        var document = client.getDocument();
        assertNotNull(document.getElementById("config-form"));
        assertEquals("0.4", document.getInputElementById("aimResponse").getValue());
        document.getInputElementById("aimResponse").setValue("0.65");
        document.getInputElementById("torpedoCooldownMin").setValue("18");
        document.getElementById("save-config").click();
        assertEquals("0.65", service.findById("TORPEDO_BOAT_BOT.AIM_RESPONSE").orElseThrow().value());
        assertEquals("18.0", service.findById("TORPEDO_BOAT_BOT.TORPEDO_COOLDOWN_MIN").orElseThrow().value());
        service.refresh();
        assertEquals(.65, service.current().torpedoBoatBot().aimResponse());
        assertEquals(18, service.current().torpedoBoatBot().torpedoCooldownMin());
    }

    @Test void invalidFieldsAndCrossFieldValuesPreventAllWrites() throws Exception {
        var service = service();
        var client = context(service, "ADMIN").openPage("/admin/game-config.html");
        var document = client.getDocument();
        var original = service.findAll();
        document.getInputElementById("aimResponse").setValue("");
        document.getElementById("save-config").click();
        assertEquals(original, service.findAll());
        document.getInputElementById("aimResponse").setValue("0.65");
        document.getInputElementById("fireMinRange").setValue("20000");
        document.getElementById("save-config").click();
        assertEquals(original, service.findAll());
        assertTrue(document.getElementById("validation-messages").getInnerText().contains("Schussbereiche"));
        document.getInputElementById("fireMinRange").setValue("3900");
        document.getInputElementById("aimTurnDamping").setValue("-1");
        document.getElementById("save-config").click();
        assertEquals(original, service.findAll());
        assertTrue(document.getElementById("validation-messages").getInnerText().contains("mindestens"));
        document.getInputElementById("aimTurnDamping").setValue("0.45");
        document.getInputElementById("targetPreferredAttackers").setValue("1.5");
        document.getElementById("save-config").click();
        assertEquals(original, service.findAll());
    }
    @Test void outOfRangeDatabaseValuesAreDisplayedAndCanBeCorrected() throws Exception {
        var service = service();
        for (String name : new String[] {"FIRE_ARC", "FIRE_MIN_RANGE"}) {
            var row = service.findById("TORPEDO_BOAT_BOT." + name).orElseThrow();
            service.save(new GameConfigParameter(row.id(), row.group(), row.type(), row.name(),
                    row.title(), row.description(), name.equals("FIRE_ARC") ? "4.0" : "1000"));
        }
        var client = context(service, "ADMIN").openPage("/admin/game-config.html");
        var document = client.getDocument();
        assertEquals(Math.toDegrees(4), Double.parseDouble(document.getInputElementById("fireArc").getValue()));
        assertEquals(20000, Double.parseDouble(document.getInputElementById("fireMinRange").getValue()));
        document.getInputElementById("fireArc").setValue(Double.toString(Math.toDegrees(.16)));
        document.getInputElementById("fireMinRange").setValue("3900");
        document.getElementById("save-config").click();
        service.refresh();
        assertEquals(.16, service.current().torpedoBoatBot().fireArc());
        assertEquals(195, service.current().torpedoBoatBot().fireMinRange());
    }

    @Test void unparseableDatabaseValueDoesNotPreventOpeningAndRepairingTheForm() throws Exception {
        var service = service();
        var row = service.findById("TORPEDO_BOAT_BOT.AIM_RESPONSE").orElseThrow();
        service.save(new GameConfigParameter(row.id(), row.group(), row.type(), row.name(),
                row.title(), row.description(), "not-a-number"));
        var document = context(service, "ADMIN").openPage("/admin/game-config.html").getDocument();
        assertNotNull(document.getElementById("config-form"));
        assertEquals("", document.getInputElementById("aimResponse").getValue());
        document.getInputElementById("aimResponse").setValue("0.7");
        document.getElementById("save-config").click();
        service.refresh();
        assertEquals(.7, service.current().torpedoBoatBot().aimResponse());
    }

    @Test void displayUnitsAreMetersAndDegreesAndAreConvertedBackWhenSaving() throws Exception {
        var service = service();
        var document = context(service, "ADMIN").openPage("/admin/game-config.html").getDocument();
        assertEquals(3900, Double.parseDouble(document.getInputElementById("fireMinRange").getValue()));
        assertEquals(Math.toDegrees(.16), Double.parseDouble(document.getInputElementById("fireArc").getValue()));
        document.getInputElementById("fireMinRange").setValue("4000");
        document.getInputElementById("fireArc").setValue("10");
        document.getElementById("save-config").click();
        service.refresh();
        assertEquals(200, service.current().torpedoBoatBot().fireMinRange());
        assertEquals(Math.toRadians(10), service.current().torpedoBoatBot().fireArc());
    }

    @Test void administratorCanRestartAndSelectAnExistingSetupWithoutSubmittingBotParameters() throws Exception {
        var service = service();
        var document = context(service, "ADMIN").openPage("/admin/game-config.html").getDocument();
        var original = service.findAll();
        document.getInputElementById("aimResponse").setValue("");
        document.getElementById("restart-game").click();
        assertEquals(1, game.restarts);
        ((one.xis.test.dom.SelectElement) document.getElementById("setup-choice")).setValue("two-ship-duel");
        document.getElementById("apply-setup").click();
        assertEquals("two-ship-duel", game.selected);
        assertEquals(original, service.findAll());
    }

    @Test void administratorSelectsBurnDownOnlyWhenStartingANewRound() throws Exception {
        var document = context(service(), "ADMIN").openPage("/admin/game-config.html").getDocument();
        ((one.xis.test.dom.SelectElement) document.getElementById("mode-choice")).setValue("BURN_DOWN");
        assertEquals("ENDLESS",game.mode);
        document.getElementById("apply-setup").click();
        assertEquals("BURN_DOWN",game.mode);
        document.getElementById("restart-game").click();
        assertEquals("BURN_DOWN",game.mode);
    }

    @Test void coloursHaveIndependentFleetSizesAndInvalidCountsAreRejected() throws Exception {
        var service = service();
        var document = context(service, "ADMIN").openPage("/admin/game-config.html").getDocument();
        var original = service.findAll();
        document.getInputElementById("fleet-light").setValue("-1");
        document.getElementById("save-fleet-size").click();
        assertEquals(original, service.findAll());
        document.getInputElementById("fleet-light").setValue("12");
        document.getInputElementById("fleet-dark").setValue("28");
        document.getElementById("save-fleet-size").click();
        service.refresh();
        assertEquals(new FleetSizeConfig(12,28,3,3,1,1), service.current().fleetSize());
        assertEquals(.4,service.current().torpedoBoatBot().aimResponse());
    }

    @Test void allThreeTypesAreIndependentForEachPartyAndTheTotalIsComputed() throws Exception {
        var service = service();
        var document = context(service,"ADMIN").openPage("/admin/game-config.html").getDocument();
        document.getInputElementById("fleet-light").setValue("4");
        document.getInputElementById("fleet-dark").setValue("8");
        document.getInputElementById("fleet-light-submarines").setValue("2");
        document.getInputElementById("fleet-dark-submarines").setValue("3");
        document.getInputElementById("fleet-light-planes").setValue("0");
        document.getInputElementById("fleet-dark-planes").setValue("1");
        document.getElementById("save-fleet-size").click();
        service.refresh();
        assertEquals(new FleetSizeConfig(4,8,2,3,0,1),service.current().fleetSize());
        assertEquals(6,service.current().fleetSize().light());
        assertEquals(12,service.current().fleetSize().dark());
    }

}
