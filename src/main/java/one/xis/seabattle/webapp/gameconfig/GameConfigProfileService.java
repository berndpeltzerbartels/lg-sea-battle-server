package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Optional;

public interface GameConfigProfileService {
    List<GameConfigProfile.Summary> findAll();
    Optional<GameConfigProfile> findById(String id);
    GameConfigProfile save(String id, String name, List<GameConfigParameter> parameters, String setupId, String mode);
}
