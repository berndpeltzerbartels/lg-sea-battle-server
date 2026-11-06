package one.xis.seabattle.webapp.gameconfig;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigProfileJsonTest {
    @Test void exportRoundTripsUnicodeNamesAndValuesWithoutExportingAnIdentity() {
        var parameters=List.of(new GameConfigParameter("FLEET.LIGHT_PLANES",GameConfigParameterGroup.FLEET,GameConfigParameterType.NUMBER,"LIGHT_PLANES","Title","Description","3"));
        String json=GameConfigProfileJson.write("Übung \"Nordsee\"","islands","ENDLESS",parameters);
        var imported=GameConfigProfileJson.read(json);
        assertEquals("Übung \"Nordsee\"",imported.name());assertEquals("3",imported.parameters().get("FLEET.LIGHT_PLANES"));
        assertFalse(json.contains("profileId"));assertEquals(1,imported.version());
    }
    @Test void malformedOrUnsupportedJsonProducesAFormValidationError() {
        for(String json:List.of("not json","{}","{\"version\":2,\"parameters\":{}}","[]"))
            assertThrows(one.xis.validation.ValidationFailedException.class,()->GameConfigProfileJson.read(json));
    }
}
