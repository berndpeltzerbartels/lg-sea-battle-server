package one.xis.seabattle.game;

import one.xis.sql.CrudRepository;
import one.xis.sql.Repository;
import one.xis.sql.Select;

import java.util.List;

@Repository
interface LandscapeModelRepository extends CrudRepository<LandscapeModelEntity, String> {

    @Select("select * from landscape_models order by created_at desc")
    List<LandscapeModelEntity> findAllNewestFirst();
}
