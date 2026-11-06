package one.xis.seabattle.webapp.gameconfig;

import com.google.gson.*;
import java.util.*;

/** Portable JSON contains settings, never database IDs or authentication data. */
final class GameConfigProfileJson {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    record Document(int version, String name, String setupId, String mode, Map<String,String> parameters) { }
    static String write(String name, String setupId, String mode, List<GameConfigParameter> parameters) {
        var values = new TreeMap<String,String>();
        parameters.forEach(p -> values.put(p.id(),p.value()));
        return JSON.toJson(new Document(1,name,setupId,mode,values));
    }
    static Document read(String json) { return read(json, "/transfer/json"); }
    static Document read(String json, String errorPath) {
        try {
            if (json == null || json.length()>1024*1024) throw new IllegalArgumentException();
            var root=JsonParser.parseString(json).getAsJsonObject();
            if (root.get("version").getAsInt()!=1) throw new IllegalArgumentException();
            var values=new LinkedHashMap<String,String>();
            for (var entry:root.getAsJsonObject("parameters").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                values.put(entry.getKey(),entry.getValue().getAsString());
            }
            return new Document(1,root.get("name").getAsString(),root.get("setupId").getAsString(),root.get("mode").getAsString(),Map.copyOf(values));
        } catch (RuntimeException invalid) {
            throw new one.xis.validation.ValidationFailedException(errorPath, "gameConfig.profileJsonInvalid");
        }
    }
}
