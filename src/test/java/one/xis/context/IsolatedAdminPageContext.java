package one.xis.context;

import one.xis.context.IntegrationTestContext.UserContextFaker;
import one.xis.auth.UserAccount;
import one.xis.http.RestControllerService;

/** Real XIS DOM/validation tests without creating game services, schedulers or application repositories. */
public final class IsolatedAdminPageContext {
    private final AppContext context;
    private final UserContextFaker user;

    public IsolatedAdminPageContext(Object service, Class<?> page, Class<?> validator, UserAccount account, Object... dependencies) {
        var builder = new AppContextBuilderImpl() {
            @Override protected PackageScanResult scanResult(Annotations annotations) {
                var result = super.scanResult(annotations);
                result.getAnnotatedComponentClasses().removeIf(type -> type.getName().startsWith("one.xis.seabattle."));
                result.getProxyInterfacesByFactory().values().forEach(types ->
                        types.removeIf(type -> type.getName().startsWith("one.xis.seabattle.")));
                return result;
            }
        };
        user = new UserContextFaker();
        user.setUserAccount(account);
        context = builder.withXIS().withSingleton(user).withSingleton(service).withSingletons(dependencies)
                .withSingletonClass(page).withSingletonClass(validator)
                .withSingletonClass(one.xis.seabattle.webapp.validation.RangeValidator.class)
                .withSingletonClass(TestUserAccountService.class)
                .withSingletonClass(TestLocalCredentialService.class).build();
    }

    public TestClient openPage(String uri) {
        var environment = new IntegrationTestEnvironment(new BackendBridge(context.getSingleton(RestControllerService.class)));
        environment.getIntegrationTestScript().reset();
        var client = new TestClient(context, environment);
        client.openPage(uri);
        return client;
    }
}
