package dev.dragongamer112.polycraftingtable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CraftingRestrictionPolicyTest {
    private final CraftingRestrictionPolicy defaults = new CraftingRestrictionPolicy(
            List.of("minecraft:", "polycraftingtable:"), List.of()
    );

    @Test
    void defaultNamespacesAllowVanillaAndThisModOnly() {
        assertTrue(defaults.allows("datapack:table", "polycraftingtable:polymer_crafting_table"));
        assertTrue(defaults.allows("other_mod:sticks", "minecraft:stick"));
        assertFalse(defaults.allows("minecraft:custom_recipe", "other_mod:ordinary_non_polymer_item"));
        assertFalse(defaults.allows("datapack:custom_recipe", "polymer_mod:server_item"));
        assertFalse(defaults.allows("datapack:empty", ""));
        assertFalse(defaults.allows("datapack:fake", "minecraft_extra:stick"));
    }

    @Test
    void exactItemsDoNotExemptSiblingsAndIgnoreClientDisguises() {
        var policy = new CraftingRestrictionPolicy(List.of("other_mod:allowed"), List.of());
        assertTrue(policy.allows("pack:a", "other_mod:allowed"));
        assertFalse(policy.allows("pack:a", "other_mod:allowed_extra"));
        assertFalse(policy.allows("pack:a", "other_mod:other"));
        // The API is passed the registered item ID; no components or client disguise
        // can turn a real custom ID into its vanilla-rendered appearance.
        assertFalse(defaults.allows("pack:a", "other_mod:paper_disguise"));
        assertTrue(defaults.allows("pack:a", "minecraft:paper"));
    }

    @Test
    void recipeRestrictionWinsOverNamespaceAndExactItem() {
        var policy = new CraftingRestrictionPolicy(
                List.of("minecraft:", "mod:output"), List.of("pack:cheap", "pack:modded")
        );
        assertFalse(policy.allows("pack:cheap", "minecraft:diamond"));
        assertTrue(policy.allows("pack:expensive", "minecraft:diamond"));
        assertFalse(policy.allows("pack:modded", "mod:output"));
        assertTrue(policy.allows("pack:alternative", "mod:output"));
    }

    @Test
    void bookPolicyRejectsUnknownMixedAndExplicitlyRestrictedDisplays() {
        assertTrue(defaults.allowsDisplay("pack:a", List.of("minecraft:stick")));
        assertTrue(defaults.allowsDisplay("pack:a",
                List.of("minecraft:stick", "polycraftingtable:polymer_crafting_table")));
        assertFalse(defaults.allowsDisplay("pack:a", List.of()));
        assertFalse(defaults.allowsDisplay("pack:a", List.of("")));
        assertFalse(defaults.allowsDisplay("pack:a", List.of("minecraft:stick", "other_mod:item")));
        var restricted = new CraftingRestrictionPolicy(List.of("minecraft:"), List.of("pack:a"));
        assertFalse(restricted.allowsDisplay("pack:a", List.of("minecraft:stick")));
    }

    @Test
    void emptyRulesRestrictEverythingAndDefensivelyCopyTheirInput() {
        assertFalse(new CraftingRestrictionPolicy(List.of(), List.of()).allows("pack:a", "minecraft:stick"));
        var exemptions = new ArrayList<>(List.of("minecraft:"));
        var restricted = new ArrayList<String>();
        var policy = new CraftingRestrictionPolicy(exemptions, restricted);
        exemptions.clear();
        restricted.add("pack:a");
        assertTrue(policy.allows("pack:a", "minecraft:stick"));
    }

    @Test
    void reportsUnknownRulesWithoutDiscardingThem() {
        var policy = new CraftingRestrictionPolicy(
                List.of("minecraft:", "absent:", "mod:missing"), List.of("pack:missing")
        );
        assertEquals(List.of("crafting recipe pack:missing", "output item mod:missing", "output namespace absent:"),
                policy.unknownRules(Set.of("minecraft:stick"), Set.of("pack:present")));
        assertTrue(policy.allows("pack:a", "absent:future_item"));
        assertFalse(policy.allows("pack:missing", "minecraft:stick"));
        assertTrue(policy.unknownRules(Set.of("minecraft:stick", "absent:item", "mod:missing"),
                Set.of("pack:missing")).isEmpty());
    }

    @Test
    void selectsFirstAllowedMatchAndNeverAssemblesUnmatchedRecipes() {
        record Candidate(String id, String output, boolean matches) {}
        var candidates = List.of(
                new Candidate("pack:unmatched", "minecraft:diamond", false),
                new Candidate("pack:blocked", "other_mod:item", true),
                new Candidate("pack:allowed", "minecraft:stick", true),
                new Candidate("pack:later", "minecraft:diamond", true)
        );
        var evaluated = new ArrayList<String>();
        var result = RecipeSelection.firstAllowed(candidates, Candidate::matches, candidate -> {
            assertTrue(candidate.matches(), "Output evaluated for an unmatched recipe");
            evaluated.add(candidate.id());
            return defaults.allows(candidate.id(), candidate.output());
        });
        assertEquals("pack:allowed", result.orElseThrow().id());
        assertEquals(List.of("pack:blocked", "pack:allowed"), evaluated);
        assertTrue(RecipeSelection.firstAllowed(candidates, Candidate::matches, candidate -> false).isEmpty());
    }

    @Test
    void dynamicCandidatesAreReevaluatedInsteadOfReusingAnOldDecision() {
        var outputs = new ArrayList<>(List.of("minecraft:stick"));
        assertTrue(RecipeSelection.firstAllowed(outputs, id -> true, defaults::allowsOutput).isPresent());
        outputs.set(0, "other_mod:item");
        assertTrue(RecipeSelection.firstAllowed(outputs, id -> true, defaults::allowsOutput).isEmpty());
    }
}
