package one.xis.seabattle;

import one.xis.sql.CrudRepository;
import one.xis.sql.Repository;

@Repository
interface PlaySessionRepository extends CrudRepository<PlaySessionEntity, String> {
}
