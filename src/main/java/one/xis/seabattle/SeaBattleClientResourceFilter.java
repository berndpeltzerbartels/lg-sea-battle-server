package one.xis.seabattle;

import one.xis.context.Component;
import one.xis.http.ContentType;
import one.xis.http.FilterChain;
import one.xis.http.HttpFilter;
import one.xis.http.HttpMethod;
import one.xis.http.HttpRequest;
import one.xis.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;

@Component
public class SeaBattleClientResourceFilter implements HttpFilter {

    private static final String CLIENT_RESOURCE_ROOT = "/sea-battle-client";
    private static final String INDEX_RESOURCE = CLIENT_RESOURCE_ROOT + "/index.html";

    @Override
    public void doFilter(HttpRequest request, HttpResponse response, FilterChain chain) {
        if (request.getHttpMethod() != HttpMethod.GET) {
            chain.doFilter(request, response);
            return;
        }

        String resourcePath = clientResourcePath(request.getPath());
        if (resourcePath == null) {
            chain.doFilter(request, response);
            return;
        }

        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatusCode(200);
            response.setContentType(contentType(resourcePath));
            response.addHeader("Cache-Control", cacheControl(resourcePath));
            response.setBody(input.readAllBytes());
        } catch (IOException e) {
            response.setStatusCode(500);
            response.setContentType(ContentType.TEXT_PLAIN);
            response.setBody("Could not load Sea Battle client resource.");
        }
    }

    @Override
    public int getPriority() {
        return 10;
    }

    private String clientResourcePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if ("/sea-battle/app".equals(path)) {
            return INDEX_RESOURCE;
        }
        if (path.contains("..") || path.contains("\\") || path.contains(":")) {
            return null;
        }
        if (path.startsWith("/sea-battle/assets/") || "/sea-battle/webgpu.html".equals(path)) {
            return CLIENT_RESOURCE_ROOT + path.substring("/sea-battle".length());
        }
        return null;
    }

    private ContentType contentType(String resourcePath) {
        if (resourcePath.endsWith(".html")) {
            return ContentType.TEXT_HTML_UTF8;
        }
        if (resourcePath.endsWith(".js")) {
            return ContentType.JAVASCRIPT;
        }
        if (resourcePath.endsWith(".css")) {
            return ContentType.CSS;
        }
        if (resourcePath.endsWith(".svg")) {
            return ContentType.SVG;
        }
        if (resourcePath.endsWith(".png")) {
            return ContentType.PNG;
        }
        if (resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg")) {
            return ContentType.JPEG;
        }
        return ContentType.APPLICATION_OCTET_STREAM;
    }

    private String cacheControl(String resourcePath) {
        return "no-store";
    }
}
