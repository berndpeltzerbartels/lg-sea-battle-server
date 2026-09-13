package one.xis.seabattle.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class MemoryGamePropertyRepository implements GamePropertyRepository {

    private final Map<String, GamePropertyEntity> rows = new LinkedHashMap<>();

    @Override
    public Optional<GamePropertyEntity> findById(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<GamePropertyEntity> findAll() {
        return List.copyOf(rows.values());
    }

    @Override
    public GamePropertyEntity save(GamePropertyEntity entity) {
        rows.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public boolean delete(GamePropertyEntity entity) {
        return entity != null && deleteById(entity.getId());
    }

    @Override
    public boolean deleteById(String id) {
        return rows.remove(id) != null;
    }

    @Override
    public long count() {
        return rows.size();
    }
}
