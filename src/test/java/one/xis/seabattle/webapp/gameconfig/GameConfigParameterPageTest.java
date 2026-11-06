package one.xis.seabattle.webapp.gameconfig;

import one.xis.auth.UserAccountImpl;
import one.xis.context.IsolatedAdminPageContext;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigParameterPageTest {
    private final TestAdministration game=new TestAdministration();
    private final GameConfigProfileService profiles=new GameConfigProfileServiceImpl(new GameConfigProfileServiceTest.MemoryRepository());
    private static class TestAdministration implements one.xis.seabattle.webapp.admin.GameAdministration {
        String selected="dense-land",mode="ENDLESS";int restarts;
        public java.util.List<SetupOption> setups(){return java.util.List.of(new SetupOption("dense-land","Inselgruppe"),new SetupOption("two-ship-duel","Zwei Schiffe"));}
        public String currentSetup(){return selected;}
        public String currentMode(){return mode;}
        public void restart(){restarts++;}
        public void selectSetup(String id){selected=id;restarts++;}
        public void start(String id,String mode){this.mode=mode;selectSetup(id);}
    }
    private GameConfigServiceImpl service() throws Exception {
        return new GameConfigServiceImpl(new GameConfigRuntimeTest.MemoryRepository(GameConfigRuntimeTest.defaults(true)));
    }
    private IsolatedAdminPageContext context(GameConfigService service,String role){
        UserAccountImpl user=null;
        if(role!=null){user=new UserAccountImpl();user.setUserId("admin-test");user.setRoles(Set.of(role));}
        return new IsolatedAdminPageContext(service,GameConfigParameterPage.class,GameConfigFormValidator.class,user,game,profiles);
    }
    @Test void guestsAndNonAdminsCannotReadOrEditProfiles() throws Exception {
        for(String role:new String[]{null,"USER"}){
            var client=context(service(),role).openPage("/admin/game-config.html");
            assertTrue(client.getWindow().location.href.contains("/login.html"));
            assertNull(client.getDocument().getElementById("settings-form"));
        }
    }
    @Test void applyUsesAllCurrentEditorValuesButDoesNotCreateAProfile() throws Exception {
        var service=service();var client=context(service,"ADMIN").openPage("/admin/game-config.html");var document=client.getDocument();
        document.getInputElementById("aimResponse").setValue("0.65");
        document.getInputElementById("fleet-light-planes").setValue("7");
        document.getElementById("save-config").click();
        service.refresh();assertEquals(.65,service.current().torpedoBoatBot().aimResponse());
        assertEquals(7,service.current().fleetSize().lightPlanes());assertTrue(profiles.findAll().isEmpty());
    }
    @Test void profileSaveCapturesUnsavedFormValuesWithoutChangingTheRunningConfiguration() throws Exception {
        var service=service();var before=service.findAll();var client=context(service,"ADMIN").openPage("/admin/game-config.html");var document=client.getDocument();
        document.getInputElementById("profile-name").setValue("Easy");
        document.getInputElementById("aimResponse").setValue("0.65");
        document.getInputElementById("fleet-light-planes").setValue("7");
        document.getElementById("save-profile").click();
        assertEquals(before,service.findAll());assertEquals(0,game.restarts);
        var profile=profiles.findById(profiles.findAll().get(0).id()).orElseThrow();
        assertEquals("7",profile.values().get("FLEET.LIGHT_PLANES"));
        assertEquals(.65,Double.parseDouble(profile.values().get("TORPEDO_BOAT_BOT.AIM_RESPONSE")));
        assertTrue(client.getWindow().location.href.contains(profile.id()));
    }
    @Test void renameKeepsTheSameIdAndReloadFindsTheNewNameAndValues() throws Exception {
        var service=service();var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        client.getDocument().getInputElementById("profile-name").setValue("First");client.getDocument().getElementById("save-profile").click();
        String id=profiles.findAll().get(0).id();
        client.getDocument().getInputElementById("profile-name").setValue("Renamed");
        client.getDocument().getInputElementById("aimResponse").setValue("0.8");client.getDocument().getElementById("save-profile").click();
        assertEquals(1,profiles.findAll().size());assertEquals(id,profiles.findAll().get(0).id());
        var reopened=context(service,"ADMIN").openPage("/admin/game-config.html?profile="+id);
        assertEquals("Renamed",reopened.getDocument().getInputElementById("profile-name").getValue());
        assertEquals("0.8",reopened.getDocument().getInputElementById("aimResponse").getValue());
    }
    @Test void duplicateNamesAreRejectedOnCreationAndRename() throws Exception {
        var service=service();profiles.save(null,"Taken",service.findAll(),"dense-land","ENDLESS");
        var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        client.getDocument().getInputElementById("profile-name").setValue(" taken ");client.getDocument().getElementById("save-profile").click();
        assertEquals(1,profiles.findAll().size());assertTrue(client.getDocument().getElementById("settings-form").getInnerText().contains("existiert bereits"));
        client.getDocument().getInputElementById("profile-name").setValue("Other");client.getDocument().getElementById("save-profile").click();
        var id=profiles.findAll().stream().filter(p->p.name().equals("Other")).findFirst().orElseThrow().id();
        client.getDocument().getInputElementById("profile-name").setValue("TAKEN");client.getDocument().getElementById("save-profile").click();
        assertEquals("Other",profiles.findById(id).orElseThrow().name());
    }
    @Test void copyingGetsANewIdentityAndUsesTheCurrentlyEditedValues() throws Exception {
        var service=service();var original=profiles.save(null,"Easy",service.findAll(),"dense-land","ENDLESS");
        var client=context(service,"ADMIN").openPage("/admin/game-config.html?profile="+original.id());
        client.getDocument().getInputElementById("aimResponse").setValue("0.9");client.getDocument().getElementById("copy-profile").click();
        var copy=profiles.findAll().stream().filter(p->!p.id().equals(original.id())).findFirst().orElseThrow();
        assertEquals("Easy (Kopie)",copy.name());
        assertEquals(.9,Double.parseDouble(profiles.findById(copy.id()).orElseThrow().values().get("TORPEDO_BOAT_BOT.AIM_RESPONSE")));
        assertEquals(original,profiles.findById(original.id()).orElseThrow());
    }
    @Test void importAndExportKeepDraftsAndNeverOverwriteAnExistingProfile() throws Exception {
        var service=service();var before=service.findAll();var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        client.getDocument().getInputElementById("profile-name").setValue("JSON test");
        client.getDocument().getInputElementById("aimResponse").setValue("0.7");client.getDocument().getElementById("export-profile").click();
        String json=client.getDocument().getInputElementById("profile-json").getValue();
        assertTrue(json.contains("JSON test"));assertFalse(json.contains("profileId"));
        String url=client.getDocument().getElementById("download-profile").getAttribute("href");
        assertTrue(url.startsWith("data:application/json;base64,"));
        assertEquals(json,new String(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',')+1)),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("0.7",client.getDocument().getInputElementById("aimResponse").getValue());
        client.getDocument().getInputElementById("profile-file").setFile("profile.json","application/json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        client.getDocument().getElementById("import-file").click();
        assertEquals("JSON test",client.getDocument().getInputElementById("profile-name").getValue());
        assertTrue(profiles.findAll().isEmpty());assertEquals(before,service.findAll());assertEquals(0,game.restarts);
    }
    @Test void numericLimitsAndCrossFieldValidationPreventWrites() throws Exception {
        var service=service();var before=service.findAll();var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        client.getDocument().getInputElementById("aimResponse").setValue("");client.getDocument().getElementById("save-config").click();assertEquals(before,service.findAll());
        client.getDocument().getInputElementById("aimResponse").setValue("0.4");client.getDocument().getInputElementById("fireMinRange").setValue("20000");
        client.getDocument().getElementById("save-config").click();assertEquals(before,service.findAll());
        client.getDocument().getInputElementById("fireMinRange").setValue("3900");client.getDocument().getInputElementById("fleet-dark-planes").setValue("-1");
        client.getDocument().getElementById("save-config").click();assertEquals(before,service.findAll());
    }
    @Test void badDatabaseValuesRemainVisibleForRepair() throws Exception {
        var service=service();var row=service.findById("TORPEDO_BOAT_BOT.FIRE_MIN_RANGE").orElseThrow();
        service.save(new GameConfigParameter(row.id(),row.group(),row.type(),row.name(),row.title(),row.description(),"1000"));
        var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        assertEquals(20000,Double.parseDouble(client.getDocument().getInputElementById("fireMinRange").getValue()));
        client.getDocument().getInputElementById("fireMinRange").setValue("3900");client.getDocument().getElementById("save-config").click();
        service.refresh();assertEquals(195,service.current().torpedoBoatBot().fireMinRange());
    }
    @Test void burnDownModeSurvivesProfileEditingAndOnlyStartsOnExplicitRequest() throws Exception {
        var service=service();var profile=profiles.save(null,"Burn",service.findAll(),"dense-land","BURN_DOWN");
        var client=context(service,"ADMIN").openPage("/admin/game-config.html?profile="+profile.id());
        assertTrue(((one.xis.test.dom.OptionElement)client.getDocument().querySelector("#mode-choice option[value=\"BURN_DOWN\"]")).isSelected());
        client.getDocument().getInputElementById("profile-name").setValue("Burn renamed");client.getDocument().getElementById("save-profile").click();
        assertEquals("BURN_DOWN",profiles.findById(profile.id()).orElseThrow().mode());
        assertEquals("ENDLESS",game.mode);
        client.getDocument().getElementById("apply-setup").click();assertEquals("BURN_DOWN",game.mode);assertEquals(1,game.restarts);
    }

    @Test void jsonFileUploadOpensANewDraftWithoutSavingOrApplyingIt() throws Exception {
        var service=service();var before=service.findAll();
        String json=GameConfigProfileJson.write("Imported file","dense-land","ENDLESS",service.findAll());
        var client=context(service,"ADMIN").openPage("/admin/game-config.html");
        client.getDocument().getInputElementById("profile-file").setFile("profile.json","application/json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        client.getDocument().getElementById("import-file").click();
        assertEquals("Imported file",client.getDocument().getInputElementById("profile-name").getValue());
        assertTrue(profiles.findAll().isEmpty());assertEquals(before,service.findAll());assertEquals(0,game.restarts);
    }

}
