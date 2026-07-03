package one.xis.seabattle;

import java.util.Optional;

interface AccountService {

    Account saveAccount(Account account);

    Optional<Account> findAccountById(String id);
}
