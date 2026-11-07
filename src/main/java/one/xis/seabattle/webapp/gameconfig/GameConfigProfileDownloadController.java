package one.xis.seabattle.webapp.gameconfig;

import one.xis.http.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/** The unguessable, expiring ticket grants access only to its prepared JSON file. */
@Controller
class GameConfigProfileDownloadController {
    static final String PATH = "/admin/game-config/export/";
    private final GameConfigProfileDownloads downloads;

    GameConfigProfileDownloadController(GameConfigProfileDownloads downloads) {
        this.downloads = downloads;
    }

    @Get(PATH + "{ticket}")
    public void download(@PathVariable("ticket") String ticket, HttpResponse response) {
        var download = downloads.take(ticket);
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Referrer-Policy", "no-referrer");
        response.addHeader("X-Content-Type-Options", "nosniff");
        if (download == null) {
            response.setStatusCode(404);
            return;
        }
        response.setStatusCode(200);
        response.setContentType(ContentType.JSON_UTF8);
        String asciiName = download.fileName().replaceAll("[^\\x20-\\x7E]", "_");
        String encodedName = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + asciiName
                + "\"; filename*=UTF-8''" + encodedName);
        response.setBody(download.json().getBytes(StandardCharsets.UTF_8));
    }
}
