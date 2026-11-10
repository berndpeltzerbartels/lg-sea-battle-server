package one.xis.context;

import one.xis.http.RestControllerService;

/** Native XIS page/frontlet rendering without starting any game services. */
public final class IsolatedControllerSetupContext {
    private static Runnable export;
    public static void exportResponses() { export.run(); }
    public static TestClient open(Class<?>... components) {
        var builder = new AppContextBuilderImpl() {
            @Override protected PackageScanResult scanResult(Annotations annotations) {
                var result = super.scanResult(annotations);
                result.getAnnotatedComponentClasses().removeIf(type -> type.getName().startsWith("one.xis.seabattle."));
                result.getProxyInterfacesByFactory().values().forEach(types -> types.removeIf(type -> type.getName().startsWith("one.xis.seabattle.")));
                return result;
            }
        };
        var configured = builder.withXIS().withSingletonClass(TestUserAccountService.class)
                .withSingletonClass(TestLocalCredentialService.class);
        for (Class<?> component : components) configured.withSingletonClass(component);
        var context = configured.build();
        var responses = new java.util.LinkedHashMap<String, Object>();
        var http = context.getSingleton(RestControllerService.class);
        var environment = new IntegrationTestEnvironment(new BackendBridge(http) {
            @Override public JavascriptResponse invokeBackend(String method, String uri, java.util.Map<String,String> headers, String body) {
                var response = super.invokeBackend(method, uri, headers, body);
                responses.put(method + " " + uri + (body == null ? "" : " " + body), response);
                return response;
            }
        });
        environment.getIntegrationTestScript().reset();
        var client = new TestClient(context, environment);
        client.openPage("/controller.html");
        try {
            var dir = java.nio.file.Path.of("build/test-fixtures"); java.nio.file.Files.createDirectories(dir);
            // The actual XIS client/runtime and recorded controller responses are replayed by browser tests.
            var bundle = new HttpTestResponse();
            http.doInvocation(new HttpTestRequest(one.xis.http.HttpMethod.GET, "/bundle.min.js", null, java.util.Map.of()), bundle);
            java.nio.file.Files.writeString(dir.resolve("controller-runtime.js"), bundle.getBody());
            export = () -> {
                try { java.nio.file.Files.writeString(dir.resolve("controller-responses.json"), new com.google.gson.Gson().toJson(responses)); }
                catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            };
            exportResponses();
        } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        return client;
    }
}
