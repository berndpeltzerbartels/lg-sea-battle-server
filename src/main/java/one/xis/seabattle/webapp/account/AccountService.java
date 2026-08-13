package one.xis.seabattle.webapp.account;

import java.util.Optional;

public interface AccountService {

    Account saveAccount(Account account);

    Optional<Account> findAccountById(String id);
}
