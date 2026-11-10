package one.xis.seabattle.webapp.controller;

import one.xis.context.IsolatedControllerSetupContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerSetupPageTest {
    @Test void realXisFrontletsRenderTogetherOnOnePageWithReturnLinksAndAllVehicleFunctions() throws Exception {
        var client = IsolatedControllerSetupContext.open(ControllerSetupPage.class,
                DirectControlFrontlet.class, AimControlFrontlet.class, EngineControlFrontlet.class, RudderControlFrontlet.class,
                FireControlFrontlet.class, StationControlFrontlet.class, ZoomControlFrontlet.class,
                DepthChargeControlFrontlet.class, DiveControlFrontlet.class, ControllerExtrasFrontlet.class, SubmarinePeriscopeFrontlet.class, one.xis.seabattle.webapp.ControllerSetupStartFixturePage.class);
        var html = client.getDocument().asString();
        assertNotNull(client.getDocument().getElementById("controller-setup"));
        for (var action : new String[]{"directX", "directY", "left", "right", "up", "down", "aimX", "aimY", "aimLeft", "aimRight", "aimUp", "aimDown", "fire",
                "stationPrevious", "stationNext", "zoom", "depth-charge", "ascend", "dive", "throttleHold", "align", "help", "forward-scope", "observation-scope"})
            assertTrue(html.contains("data-controller-action=\"" + action + "\""), action);
        assertTrue(html.contains("Zurück zur Fahrzeugauswahl"));
        assertTrue(html.contains("Nur Torpedoboot")); assertTrue(html.contains("Nur U-Boot"));
        assertTrue(html.contains("Für alle Fahrzeuge"));
        assertTrue(html.contains("Sehrohr voraus")); assertTrue(html.contains("Ohne Controller:"));
        // Both return links work without saving a controller profile. Incomplete login data is allowed.
        client.getDocument().getElementById("controller-back-top").click();
        client.getDocument().getInputElementById("fixture-alias").setValue("TEST");
        ((one.xis.test.dom.SelectElementImpl)client.getDocument().getElementById("fixture-team")).setValue("dark");
        ((one.xis.test.dom.SelectElementImpl)client.getDocument().getElementById("fixture-vehicle")).setValue("submarine");
        client.getDocument().getElementById("fixture-configure").click();
        assertNotNull(client.getDocument().getElementById("controller-setup"), "Unfilled name must not block setup");
        client.getDocument().getElementById("controller-back-bottom").click();
        assertEquals("TEST", client.getDocument().getInputElementById("fixture-alias").getValue());
        assertEquals("dark", ((one.xis.test.dom.SelectElementImpl)client.getDocument().getElementById("fixture-team")).getValue());
        assertEquals("submarine", ((one.xis.test.dom.SelectElementImpl)client.getDocument().getElementById("fixture-vehicle")).getValue());
        IsolatedControllerSetupContext.exportResponses();
        var dir = java.nio.file.Path.of("build/test-fixtures");java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("controller-setup.html"), html);
    }
}
