package one.xis.seabattle.webapp.admin;

import one.xis.auth.LocalCredentialService;
import one.xis.auth.UserAccountImpl;
import one.xis.auth.UserAccountService;
import one.xis.context.Component;

import java.util.Optional;
import java.util.Set;

@Component
class SeaBattleAdminSecurity implements LocalCredentialService, UserAccountService<UserAccountImpl> {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_ROLE_LOWERCASE = "admin";

    @Override
    public boolean validateCredentials(String userId, String password) {
        return adminUserId().equals(userId) && adminPassword().equals(password);
    }

    @Override
    public void setPassword(String userId, String password) {
        throw new UnsupportedOperationException("Sea Battle admin password is configured outside the application.");
    }

    @Override
    public boolean needsRehash(String userId) {
        return false;
    }

    @Override
    public Optional<UserAccountImpl> getUserAccount(String userId) {
        if (!adminUserId().equals(userId)) {
            return Optional.empty();
        }
        UserAccountImpl account = new UserAccountImpl();
        account.setUserId(userId);
        account.setPreferredUsername(userId);
        account.setName("Sea Battle Admin");
        account.setRoles(Set.of(ADMIN_ROLE, ADMIN_ROLE_LOWERCASE));
        return Optional.of(account);
    }

    @Override
    public void saveUserAccount(UserAccountImpl userAccount) {
        throw new UnsupportedOperationException("Sea Battle admin account is read-only.");
    }

    private String adminUserId() {
        return config("seaBattle.admin.user", "SEA_BATTLE_ADMIN_USER", "admin");
    }

    private String adminPassword() {
        return config("seaBattle.admin.password", "SEA_BATTLE_ADMIN_PASSWORD", "bernd");
    }

    private String config(String propertyName, String environmentName, String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return fallback;
    }
}
