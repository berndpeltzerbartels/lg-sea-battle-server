package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientEventLogBudgetTest {
    @Test
    void limitsRepeatedReportsButAllowsRecoveryAfterTheWindow() {
        var budget = new ClientEventLogBudget();
        for (int i = 0; i < 40; i++) assertTrue(budget.allow("player", 0));
        assertFalse(budget.allow("player", 1));
        assertTrue(budget.allow("other", 1));
        assertTrue(budget.allow("player", 10_000_000_000L));
    }
}
