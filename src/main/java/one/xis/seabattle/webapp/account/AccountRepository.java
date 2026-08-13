package one.xis.seabattle.webapp.account;

import one.xis.sql.CrudRepository;
import one.xis.sql.Repository;

@Repository
interface AccountRepository extends CrudRepository<AccountEntity, String> {
}
