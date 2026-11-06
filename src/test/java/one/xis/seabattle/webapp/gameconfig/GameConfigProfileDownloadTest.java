package one.xis.seabattle.webapp.gameconfig;

import one.xis.context.*;
import one.xis.http.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigProfileDownloadTest {
    static class TimedDownloads extends GameConfigProfileDownloads {
        long now;
        @Override long nowMillis() { return now; }
    }

    @Test void ticketsAreIndependentExpiringAndSingleUse() {
        var downloads=new TimedDownloads();
        String first=downloads.prepare("one"), second=downloads.prepare("two");
        assertNotEquals(first,second);
        assertNull(downloads.take("unknown"));
        assertEquals("one",downloads.take(first));
        assertNull(downloads.take(first));
        downloads.now=60_000;
        assertNull(downloads.take(second));
    }

    @Test void realHttpControllerReturnsAnAttachmentWithExactUtf8Json() throws Exception {
        var downloads=new GameConfigProfileDownloads();
        var http=IsolatedAdminPageContext.httpControllers(downloads,new GameConfigProfileDownloadController(downloads));
        String json="{\"name\":\"Küste & Boote\",\"version\":1}";
        String ticket=downloads.prepare(json);
        var response=new HttpTestResponse();
        http.doInvocation(new HttpTestRequest(HttpMethod.GET,
                GameConfigProfileDownloadController.PATH+ticket,null,Map.of()),response);
        assertEquals(200,response.getStatusCode());
        assertEquals(ContentType.JSON_UTF8,response.getContentType());
        assertEquals(json,response.getBody()); // JSON must not be serialized a second time.
        assertEquals(java.util.List.of("attachment; filename=\"spielprofil.json\""),response.getHeaders().get("Content-Disposition"));
        assertEquals(java.util.List.of("no-store"),response.getHeaders().get("Cache-Control"));
        var fixtureDirectory=java.nio.file.Path.of("build/test-fixtures");
        java.nio.file.Files.createDirectories(fixtureDirectory);
        var headers=new java.util.HashMap<String,String>();
        response.getHeaders().forEach((name,values)->headers.put(name,values.get(0)));
        headers.put("Content-Type",response.getContentType().getValue());
        java.nio.file.Files.writeString(fixtureDirectory.resolve("game-config-download.json"),
                new com.google.gson.Gson().toJson(Map.of("url",GameConfigProfileDownloadController.PATH+ticket,
                        "headers",headers,"body",json)));
        try(var input=getClass().getResourceAsStream("/xis.min.js")) {
            java.nio.file.Files.copy(java.util.Objects.requireNonNull(input),fixtureDirectory.resolve("xis-download-runtime.js"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var missing=new HttpTestResponse();
        http.doInvocation(new HttpTestRequest(HttpMethod.GET,
                GameConfigProfileDownloadController.PATH+ticket,null,Map.of()),missing);
        assertEquals(404,missing.getStatusCode());
    }
}
