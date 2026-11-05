package one.xis.seabattle.webapp.gameconfig;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
interface GameConfigParameterMapper {
    GameConfigParameterMapper INSTANCE = Mappers.getMapper(GameConfigParameterMapper.class);

    GameConfigParameter toParameter(GameConfigParameterEntity entity);

    GameConfigParameterEntity toEntity(GameConfigParameter parameter);
}
