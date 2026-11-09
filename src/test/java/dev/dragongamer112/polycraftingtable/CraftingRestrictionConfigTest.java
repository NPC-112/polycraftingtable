package dev.dragongamer112.polycraftingtable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingRestrictionConfigTest {
    @TempDir Path directory;

    @Test
    void generatesOnlyTheDocumentedDefaultsWhenMissing() throws Exception {
        Path file = directory.resolve("config/polycraftingtable.json");
        var policy = CraftingRestrictionConfig.load(file);
        var document = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(List.of("minecraft:", "polycraftingtable:"),
                document.getAsJsonArray("vanillaCraftingExemptions").asList().stream()
                        .map(com.google.gson.JsonElement::getAsString).toList());
        assertTrue(document.getAsJsonArray("restrictedRecipeIds").isEmpty());
        assertTrue(policy.allows("pack:a", "minecraft:stick"));
        assertFalse(policy.allows("pack:a", "another_mod:item"));
    }

    @Test
    void existingEmptyListsAreNotRepopulatedOrRewritten() throws Exception {
        Path file = directory.resolve("polycraftingtable.json");
        String custom = "{ \"vanillaCraftingExemptions\": [], \"restrictedRecipeIds\": [] }\n";
        Files.writeString(file, custom);
        assertFalse(CraftingRestrictionConfig.load(file).allows("minecraft:a", "minecraft:stick"));
        assertEquals(custom, Files.readString(file));
        assertFalse(CraftingRestrictionConfig.load(file).allows("minecraft:a", "minecraft:stick"));
        assertEquals(custom, Files.readString(file));
    }

    @Test
    void editsTakeEffectOnNextLoadNotInAnExistingPolicy() throws Exception {
        Path file = directory.resolve("polycraftingtable.json");
        var before = CraftingRestrictionConfig.load(file);
        Files.writeString(file, "{\"vanillaCraftingExemptions\":[],\"restrictedRecipeIds\":[]}");
        assertTrue(before.allows("pack:a", "minecraft:stick"));
        assertFalse(CraftingRestrictionConfig.load(file).allows("pack:a", "minecraft:stick"));
    }

    @Test
    void malformedFilesFailClearlyAndAreNeverReplaced() throws Exception {
        Path file = directory.resolve("polycraftingtable.json");
        for (String json : List.of(
                "", "[]", "null", "{broken}", "{}",
                "{\"vanillaCraftingExemptions\":{},\"restrictedRecipeIds\":[]}",
                "{\"vanillaCraftingExemptions\":[true],\"restrictedRecipeIds\":[]}",
                "{\"vanillaCraftingExemptions\":[],\"restrictedRecipeIds\":null}",
                "{\"vanillaCraftingExemptions\":[],\"restrictedRecipeIds\":[],\"typo\":[]}",
                "{\"vanillaCraftingExemptions\":[],\"restrictedRecipeIds\":[]} {}",
                "{'vanillaCraftingExemptions':[], 'restrictedRecipeIds':[]}",
                "{\"vanillaCraftingExemptions\":[],\"restrictedRecipeIds\":[],}"
        )) {
            Files.writeString(file, json);
            var exception = assertThrows(IllegalStateException.class, () -> CraftingRestrictionConfig.load(file), json);
            assertTrue(exception.getMessage().contains(file.toAbsolutePath().toString()));
            assertEquals(json, Files.readString(file));
        }
    }

    @Test
    void rejectsMalformedNamespaceItemAndRecipeRules() {
        for (String rule : List.of("", ":", "Minecraft:", "minecraft", "mod:*", " mod:item",
                "mod:item ", "mod::item", "mod:", "mod:item#tag")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CraftingRestrictionPolicy(List.of(), List.of(rule)), rule);
        }
        for (String rule : List.of("", ":", "Minecraft:", "minecraft", "mod:*", "mod::item", "#mod:tag")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CraftingRestrictionPolicy(List.of(rule), List.of()), rule);
        }
        assertDoesNotThrow(() -> new CraftingRestrictionPolicy(List.of("valid_mod:", "mod:path/to-item"),
                List.of("datapack:path/to-recipe")));
    }
}
