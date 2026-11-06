package one.xis.seabattle.webapp.gameconfig;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigProfileServiceTest {
    static final class MemoryRepository implements GameConfigProfileRepository {
        final Map<String,GameConfigProfileEntity> rows = new LinkedHashMap<>();
        public List<GameConfigProfileEntity> findAll(){return List.copyOf(rows.values());}
        public Optional<GameConfigProfileEntity> findById(String id){return Optional.ofNullable(rows.get(id));}
        public GameConfigProfileEntity save(GameConfigProfileEntity entity){rows.put(entity.getId(),entity);return entity;}
        public boolean delete(GameConfigProfileEntity entity){return deleteById(entity.getId());}
        public boolean deleteById(String id){return rows.remove(id)!=null;}
        public long count(){return rows.size();}
    }
    private List<GameConfigParameter> parameters(String value) {
        return List.of(new GameConfigParameter("FLEET.LIGHT_PLANES",GameConfigParameterGroup.FLEET,
                GameConfigParameterType.NUMBER,"LIGHT_PLANES","Flugzeuge","Beschreibung",value));
    }
    @Test void profilesSurviveServiceRecreationAndRetainValuesAndRoundSelection() {
        var repository=new MemoryRepository();
        var service=new GameConfigProfileServiceImpl(repository);
        var saved=service.save(null,"  Ruhige See  ",parameters("7"),"islands","ENDLESS");
        assertEquals("Ruhige See",saved.name());
        var restored=new GameConfigProfileServiceImpl(repository).findById(saved.id()).orElseThrow();
        assertEquals(saved,restored);
        assertEquals("7",restored.values().get("FLEET.LIGHT_PLANES"));
        assertThrows(UnsupportedOperationException.class,()->restored.values().clear());
    }
    @Test void duplicateNamesCannotSilentlyReplaceAnotherProfile() {
        var repository=new MemoryRepository();var service=new GameConfigProfileServiceImpl(repository);
        var first=service.save(null,"Zulu",parameters("2"),"islands","ENDLESS");
        assertThrows(one.xis.validation.ValidationFailedException.class,()->service.save(null," zULU ",parameters("9"),"islands","ENDLESS"));
        var renamed=service.save(first.id(),"Alpha",parameters("3"),"islands","ENDLESS");
        assertEquals(first.id(),renamed.id());assertEquals(1,repository.count());
    }
    @Test void profilesKeepRawValuesWithoutCreatingASecondParameterValidation() {
        var service=new GameConfigProfileServiceImpl(new MemoryRepository());
        assertEquals("-3",service.save(null,"Repair",parameters("-3"),"islands","ENDLESS").values().get("FLEET.LIGHT_PLANES"));
        assertThrows(one.xis.validation.ValidationFailedException.class,()->service.save(null," ",parameters("3"),"islands","ENDLESS"));
        assertThrows(one.xis.validation.ValidationFailedException.class,()->service.save(null,"a".repeat(101),parameters("3"),"islands","ENDLESS"));
    }
}
