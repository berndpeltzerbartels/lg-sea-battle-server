package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeaBattleClientControllerTest {

    @Test
    void accountSessionRequiresActivePlayerRegistration() {
        SeaBattlePlayerRegistry registry = new SeaBattlePlayerRegistry();
        SeaBattleClientController controller = new SeaBattleClientController(
                null,
                null,
                null,
                registry,
                new FixedAccountService(new Account("account-1", "Bernd", "BPB", "light", null)),
                null,
                null,
                null
        );

        var inactiveResponse = controller.getPlayerSessionByAccount("account-1");
        assertEquals(403, inactiveResponse.getStatusCode());

        registry.register("player-BPB-123456789abc", "BPB", "Bernd", "light", "account-1");
        var activeResponse = controller.getPlayerSessionByAccount("account-1");

        assertEquals(200, activeResponse.getStatusCode());
        SeaBattleClientController.PlayerLogin login = (SeaBattleClientController.PlayerLogin) activeResponse.getBody();
        assertEquals("player-BPB-123456789abc", login.playerId());
    }

    private record FixedAccountService(Account account) implements AccountService {
        @Override
        public Account saveAccount(Account account) {
            return account;
        }

        @Override
        public Optional<Account> findAccountById(String id) {
            return account.id().equals(id) ? Optional.of(account) : Optional.empty();
        }
    }
}
