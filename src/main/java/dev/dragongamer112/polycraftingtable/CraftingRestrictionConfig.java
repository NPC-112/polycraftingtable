package dev.dragongamer112.polycraftingtable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict, startup-only configuration. Existing files are never rewritten. */
public final class CraftingRestrictionConfig {
    private static final String DEFAULT_JSON = """
            {
              "vanillaCraftingExemptions": [
                "minecraft:",
                "polycraftingtable:"
              ],
              "restrictedRecipeIds": []
            }
            """;
    private static final Set<String> FIELDS = Set.of("vanillaCraftingExemptions", "restrictedRecipeIds");

    private CraftingRestrictionConfig() {
    }

    public static CraftingRestrictionPolicy load(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try {
                Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
                // Read the owner's existing file, including intentional empty lists.
            }
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load crafting restrictions from " + path.toAbsolutePath()
                    + ": " + exception.getMessage()
                    + ". Fix the config and restart; the file has not been replaced.", exception);
        }
    }

    static CraftingRestrictionPolicy parse(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Expected one JSON object");
            }
            JsonObject object = value.getAsJsonObject();
            for (String key : object.keySet()) {
                if (!FIELDS.contains(key)) {
                    throw new IllegalArgumentException("Unknown config field: " + key);
                }
            }
            return new CraftingRestrictionPolicy(
                    strings(object, "vanillaCraftingExemptions"),
                    strings(object, "restrictedRecipeIds")
            );
        }
    }

    private static List<String> strings(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array of strings; use [] for no rules");
        }
        List<String> rules = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " must contain strings only");
            }
            rules.add(entry.getAsString());
        }
        return rules;
    }
}
