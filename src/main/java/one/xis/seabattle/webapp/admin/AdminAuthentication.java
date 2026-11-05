package one.xis.seabattle.webapp.admin;

import one.xis.auth.*;
import one.xis.context.Component;
import one.xis.context.Value;

import java.util.Optional;
import java.util.Set;

/** One property-configured local administrator; XIS handles passwords, login and tokens. */
@Component
class AdminAuthentication implements UserAccountService<UserAccountImpl>, LocalCredentialRepository {
    @Value(value = "seaBattle.admin.username", mandatory = false)
    private String username;
    @Value(value = "seaBattle.admin.password-hash", mandatory = false)
    private String passwordHash;

    @Override
    public Optional<StoredCredentials> findByUserId(String userId) {
        if (!configured() || !username.equals(userId)) return Optional.empty();
        return Optional.of(new StoredCredentials(username, passwordHash));
    }

    @Override
    public Optional<UserAccountImpl> getUserAccount(String userId) {
        if (!configured() || !username.equals(userId)) return Optional.empty();
        var account = new UserAccountImpl();
        account.setUserId(username);
        account.setPreferredUsername(username);
        account.setRoles(Set.of("ADMIN"));
        return Optional.of(account);
    }

    private boolean configured() {
        return username != null && !username.isBlank()
                && passwordHash != null && passwordHash.startsWith("$argon2id$");
    }

    @Override
    public void save(StoredCredentials credentials) {
        throw new UnsupportedOperationException("Admin-Zugang wird über Properties konfiguriert.");
    }

    @Override
    public void saveUserAccount(UserAccountImpl account) {
        throw new UnsupportedOperationException("Admin-Zugang wird über Properties konfiguriert.");
    }
}
