package one.xis.seabattle;

import one.xis.sql.CrudRepository;
import one.xis.sql.Param;
import one.xis.sql.Repository;
import one.xis.sql.Select;

import java.util.Optional;

@Repository
interface PlaySessionRepository extends CrudRepository<PlaySessionEntity, String> {

    @Select("select * from sessions where game_id = {gameId} and alias = {alias} and end_time is null")
    Optional<PlaySessionEntity> findActiveByGameAndAlias(@Param("gameId") String gameId, @Param("alias") String alias);
}
