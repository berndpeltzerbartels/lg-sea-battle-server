package one.xis.seabattle;

import one.xis.context.Component;

import java.util.Optional;

@Component
class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper = AccountMapper.INSTANCE;

    AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Account saveAccount(Account account) {
        AccountEntity saved = accountRepository.save(accountMapper.toEntity(account));
        return accountMapper.toAccount(saved);
    }

    @Override
    public Optional<Account> findAccountById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findById(id).map(accountMapper::toAccount);
    }
}
