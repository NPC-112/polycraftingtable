package dev.dragongamer112.polycraftingtable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable policy using real output item IDs, never Polymer client disguises. */
public final class CraftingRestrictionPolicy {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern ITEM_OR_RECIPE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final Set<String> namespaces;
    private final Set<String> items;
    private final Set<String> recipes;

    public CraftingRestrictionPolicy(List<String> exemptions, List<String> restrictedRecipes) {
        Set<String> namespaces = new LinkedHashSet<>();
        Set<String> items = new LinkedHashSet<>();
        for (String rule : exemptions) {
            if (rule != null && rule.endsWith(":")
                    && NAMESPACE.matcher(rule.substring(0, rule.length() - 1)).matches()) {
                namespaces.add(rule.substring(0, rule.length() - 1));
            } else if (isIdentifier(rule)) {
                items.add(rule);
            } else {
                throw new IllegalArgumentException("Invalid vanillaCraftingExemptions rule: " + rule
                        + "; expected namespace: or namespace:item");
            }
        }
        Set<String> recipes = new LinkedHashSet<>();
        for (String rule : restrictedRecipes) {
            if (!isIdentifier(rule)) {
                throw new IllegalArgumentException("Invalid restrictedRecipeIds rule: " + rule
                        + "; expected an exact namespace:recipe ID");
            }
            recipes.add(rule);
        }
        this.namespaces = Set.copyOf(namespaces);
        this.items = Set.copyOf(items);
        this.recipes = Set.copyOf(recipes);
    }

    private static boolean isIdentifier(String value) {
        return value != null && ITEM_OR_RECIPE.matcher(value).matches();
    }

    public boolean explicitlyRestricts(String recipeId) {
        return this.recipes.contains(recipeId);
    }

    public boolean allowsOutput(String itemId) {
        if (!isIdentifier(itemId)) {
            return false;
        }
        return this.items.contains(itemId)
                || this.namespaces.contains(itemId.substring(0, itemId.indexOf(':')));
    }

    public boolean allows(String recipeId, String outputItemId) {
        return !this.explicitlyRestricts(recipeId) && this.allowsOutput(outputItemId);
    }

    /** Unknown/empty or mixed displays must not expose a restricted variant. */
    public boolean allowsDisplay(String recipeId, Collection<String> outputItemIds) {
        return !this.explicitlyRestricts(recipeId)
                && !outputItemIds.isEmpty()
                && outputItemIds.stream().allMatch(this::allowsOutput);
    }

    public List<String> unknownRules(Set<String> registeredItems, Set<String> craftingRecipes) {
        Set<String> unknown = new java.util.TreeSet<>();
        for (String namespace : this.namespaces) {
            if (registeredItems.stream().noneMatch(id -> id.startsWith(namespace + ":"))) {
                unknown.add("output namespace " + namespace + ":");
            }
        }
        this.items.stream().filter(id -> !registeredItems.contains(id))
                .forEach(id -> unknown.add("output item " + id));
        this.recipes.stream().filter(id -> !craftingRecipes.contains(id))
                .forEach(id -> unknown.add("crafting recipe " + id));
        return List.copyOf(unknown);
    }

    public String summary() {
        return this.namespaces.size() + " namespace exemptions, " + this.items.size()
                + " item exemptions, " + this.recipes.size() + " explicit recipe restrictions";
    }
}
