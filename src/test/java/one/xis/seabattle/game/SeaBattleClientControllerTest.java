package one.xis.seabattle.game;

import one.xis.seabattle.webapp.account.Account;
import one.xis.seabattle.webapp.account.AccountService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void gameConfigExposesCurrentEngineSpeeds() {
        SeaBattleClientController controller = new SeaBattleClientController(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertArrayEquals(
                new double[]{-8.0, -2.2, 0, 0.9, 2.93, 6.18, 10.4, 12.0, 17.5},
                controller.getGameConfig().engineSpeeds()
        );
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
