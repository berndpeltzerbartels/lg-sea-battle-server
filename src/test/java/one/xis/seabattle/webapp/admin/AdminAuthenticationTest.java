package one.xis.seabattle.webapp.admin;

import one.xis.auth.Password4jLocalCredentialService;
import one.xis.auth.StoredCredentials;
import one.xis.auth.LocalCredentialRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuthenticationTest {
    @Test void noCredentialsMeansNoAdminAccountOrLogin() {
        var auth = new AdminAuthentication();
        assertTrue(auth.findByUserId("admin").isEmpty());
        assertTrue(auth.getUserAccount("admin").isEmpty());
        assertFalse(new Password4jLocalCredentialService(auth).validateCredentials("admin", ""));
    }

    @Test void configuredAccountUsesRealXisPasswordVerificationAndAdminRole() throws Exception {
        String[] hash = new String[1];
        var hashing = new Password4jLocalCredentialService(new LocalCredentialRepository() {
            public Optional<StoredCredentials> findByUserId(String id) { return Optional.empty(); }
            public void save(StoredCredentials credentials) { hash[0] = credentials.getPasswordHash(); }
        });
        hashing.setPassword("admin-test", "a-test-only-password");
        var auth = new AdminAuthentication();
        var username = AdminAuthentication.class.getDeclaredField("username");
        username.setAccessible(true);
        username.set(auth, "admin-test");
        var passwordHash = AdminAuthentication.class.getDeclaredField("passwordHash");
        passwordHash.setAccessible(true);
        passwordHash.set(auth, hash[0]);
        var credentials = new Password4jLocalCredentialService(auth);
        assertTrue(credentials.validateCredentials("admin-test", "a-test-only-password"));
        assertFalse(credentials.validateCredentials("admin-test", "wrong"));
        assertFalse(credentials.validateCredentials("someone-else", "a-test-only-password"));
        assertEquals(Set.of("ADMIN"), auth.getUserAccount("admin-test").orElseThrow().getRoles());
        assertTrue(auth.getUserAccount("someone-else").isEmpty());
    }
}
