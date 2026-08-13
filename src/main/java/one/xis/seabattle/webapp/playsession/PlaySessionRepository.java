package one.xis.seabattle.webapp.playsession;

import one.xis.sql.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
interface PlaySessionRepository extends CrudRepository<PlaySessionEntity, String> {

    @Select("select * from sessions where game_id = {gameId} and alias = {alias} and end_time is null")
    Optional<PlaySessionEntity> findActiveByGameAndAlias(@Param("gameId") String gameId, @Param("alias") String alias);

    @Update("update sessions set end_time = {endTime} where game_id = {gameId} and end_time is null")
    int endActiveByGame(@Param("gameId") String gameId, @Param("endTime") LocalDateTime endTime);
}
