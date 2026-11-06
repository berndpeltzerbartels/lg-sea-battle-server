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
        var saved=service.save("  Ruhige See  ",parameters("7"),"islands","ENDLESS");
        assertEquals("Ruhige See",saved.name());
        var restored=new GameConfigProfileServiceImpl(repository).findById(saved.id()).orElseThrow();
        assertEquals(saved,restored);
        assertEquals("7",restored.values().get("FLEET.LIGHT_PLANES"));
        assertThrows(UnsupportedOperationException.class,()->restored.values().clear());
    }
    @Test void savingTheSameNameReplacesOnlyThatProfileAndKeepsNamesSorted() {
        var repository=new MemoryRepository();var service=new GameConfigProfileServiceImpl(repository);
        service.save("Zulu",parameters("2"),"islands","ENDLESS");
        service.save("Alpha",parameters("3"),"dense-land","ENDLESS");
        var updated=service.save(" zULU ",parameters("9"),"dense-land","ENDLESS");
        assertEquals(2,repository.count());
        assertEquals(List.of("Alpha","zULU"),service.findAll().stream().map(GameConfigProfile.Summary::name).toList());
        assertEquals("9",updated.values().get("FLEET.LIGHT_PLANES"));
        assertEquals("3",service.findById("alpha").orElseThrow().values().get("FLEET.LIGHT_PLANES"));
    }
    @Test void profilesKeepRawValuesWithoutCreatingASecondParameterValidation() {
        var service=new GameConfigProfileServiceImpl(new MemoryRepository());
        assertEquals("-3",service.save("Repair",parameters("-3"),"islands","ENDLESS").values().get("FLEET.LIGHT_PLANES"));
        assertThrows(IllegalArgumentException.class,()->service.save(" ",parameters("3"),"islands","ENDLESS"));
        assertThrows(IllegalArgumentException.class,()->service.save("a".repeat(101),parameters("3"),"islands","ENDLESS"));
    }
}
