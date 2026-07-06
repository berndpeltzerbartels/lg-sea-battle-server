package one.xis.seabattle;

import one.xis.sql.CrudRepository;
import one.xis.sql.Param;
import one.xis.sql.Repository;
import one.xis.sql.Select;

import java.util.List;

@Repository
interface GameRepository extends CrudRepository<GameEntity, String> {

    @Select("select * from games where status = {status}")
    List<GameEntity> findByStatus(@Param("status") String status);
}
