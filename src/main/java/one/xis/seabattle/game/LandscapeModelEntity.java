package one.xis.seabattle.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.xis.sql.Entity;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity("landscape_models")
class LandscapeModelEntity {
    String id;
    String name;
    String sourceFormat;
    String originalJson;
    String worldMapJson;
    String respawnCandidatesJson;
    LocalDateTime createdAt;
}
