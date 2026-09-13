package one.xis.seabattle.game;

import one.xis.sql.CrudRepository;
import one.xis.sql.Repository;

@Repository
interface GamePropertyRepository extends CrudRepository<GamePropertyEntity, String> {
}
