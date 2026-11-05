package one.xis.seabattle.webapp.gameconfig;

import one.xis.sql.CrudRepository;
import one.xis.sql.Repository;

@Repository
interface GameConfigParameterRepository extends CrudRepository<GameConfigParameterEntity, String> {
}
