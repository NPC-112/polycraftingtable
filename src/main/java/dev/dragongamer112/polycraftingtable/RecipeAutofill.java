package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Plans recipe placements without changing the player's inventory. */
final class RecipeAutofill {
    private static final int GRID_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    private RecipeAutofill() {
    }

    static boolean supports(CraftingRecipe recipe) {
        if (recipe == null || recipe.isSpecial()) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
            return width >= 1 && width <= 3
                    && height >= 1 && height <= 3
                    && ingredients.size() == width * height
                    && ingredients.stream().anyMatch(ingredient ->
                    ingredient.isPresent() && !ingredient.get().isEmpty());
        }

        if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            return ingredients.size() <= GRID_SIZE
                    && ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty());
        }

        return false;
    }

    static PlanResult plan(CraftingRecipe recipe, List<ItemStack> inventory, List<ItemStack> grid) {
        if (!supports(recipe)) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }
        if (inventory.size() < INVENTORY_SIZE || grid.size() != GRID_SIZE) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }

        List<PlacementRequirement> requirements = requirements(recipe);
        if (requirements.isEmpty()) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }
        return planRequirements(requirements, inventory, grid);
    }

    static PlanResult planMaximum(
            CraftingRecipe recipe,
            List<ItemStack> inventory,
            List<ItemStack> grid
    ) {
        if (!supports(recipe)
                || inventory.size() < INVENTORY_SIZE
                || grid.size() != GRID_SIZE) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }
        return planMaximumRequirements(requirements(recipe), inventory, grid);
    }

    private static List<PlacementRequirement> requirements(CraftingRecipe recipe) {
        List<PlacementRequirement> requirements = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
            if (width < 1 || width > 3 || height < 1 || height > 3
                    || ingredients.size() != width * height) {
                return List.of();
            }

            for (int index = 0; index < ingredients.size(); index++) {
                Optional<Ingredient> ingredient = ingredients.get(index);
                if (ingredient.isPresent() && !ingredient.get().isEmpty()) {
                    int gridSlot = (index / width) * 3 + index % width;
                    requirements.add(new PlacementRequirement(gridSlot, ingredient.get()::test));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            if (ingredients.size() > GRID_SIZE) {
                return List.of();
            }
            for (int index = 0; index < ingredients.size(); index++) {
                Ingredient ingredient = ingredients.get(index);
                if (!ingredient.isEmpty()) {
                    requirements.add(new PlacementRequirement(index, ingredient::test));
                }
            }
        } else {
            return List.of();
        }

        return requirements;
    }

    static PlanResult planMaximumRequirements(
            List<PlacementRequirement> requirements,
            List<ItemStack> inventory,
            List<ItemStack> grid
    ) {
        if (requirements.isEmpty()
                || requirements.size() > GRID_SIZE
                || inventory.size() < INVENTORY_SIZE
                || grid.size() != GRID_SIZE
                || requirements.stream().map(PlacementRequirement::gridSlot).distinct().count()
                != requirements.size()) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }

        List<ItemGroup> groups = groupSources(inventory, grid);
        int upperBound = groups.stream()
                .mapToInt(ItemGroup::totalCount)
                .sum() / requirements.size();
        for (PlacementRequirement requirement : requirements) {
            int bestCapacity = groups.stream()
                    .filter(group -> requirement.matches().test(group.stack))
                    .mapToInt(ItemGroup::slotCapacity)
                    .max()
                    .orElse(0);
            upperBound = Math.min(upperBound, bestCapacity);
        }

        if (upperBound < 1) {
            return new PlanResult(null, Failure.MISSING_INGREDIENTS);
        }

        boolean sawInventorySpaceFailure = false;
        for (int crafts = upperBound; crafts >= 1; crafts--) {
            PlanResult attempt = planRepeatedRequirements(requirements, inventory, grid, crafts);
            if (attempt.succeeded()) {
                return attempt;
            }
            sawInventorySpaceFailure |= attempt.failure() == Failure.NO_INVENTORY_SPACE;
        }
        return new PlanResult(null, sawInventorySpaceFailure
                ? Failure.NO_INVENTORY_SPACE
                : Failure.MISSING_INGREDIENTS);
    }

    static PlanResult planRepeatedRequirements(
            List<PlacementRequirement> requirements,
            List<ItemStack> inventory,
            List<ItemStack> grid,
            int crafts
    ) {
        if (requirements.isEmpty()
                || requirements.size() > GRID_SIZE
                || inventory.size() < INVENTORY_SIZE
                || grid.size() != GRID_SIZE
                || crafts < 1
                || requirements.stream().map(PlacementRequirement::gridSlot).anyMatch(slot ->
                slot < 0 || slot >= GRID_SIZE)
                || requirements.stream().map(PlacementRequirement::gridSlot).distinct().count()
                != requirements.size()) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }

        List<ItemGroup> groups = groupSources(inventory, grid);
        List<List<Integer>> candidateGroups = new ArrayList<>(requirements.size());
        for (PlacementRequirement requirement : requirements) {
            List<Integer> candidates = new ArrayList<>();
            for (int group = 0; group < groups.size(); group++) {
                if (groups.get(group).slotCapacity() >= crafts
                        && requirement.matches().test(groups.get(group).stack)) {
                    candidates.add(group);
                }
            }
            candidates.sort(Comparator
                    .comparingInt((Integer group) -> groups.get(group).slotCapacity()).reversed()
                    .thenComparingInt(group -> groups.get(group).hasGridSource() ? 0 : 1));
            if (candidates.isEmpty()) {
                return new PlanResult(null, Failure.MISSING_INGREDIENTS);
            }
            candidateGroups.add(candidates);
        }

        List<Integer> order = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingInt(index -> candidateGroups.get(index).size()));

        int[] remaining = groups.stream().mapToInt(ItemGroup::totalCount).toArray();
        int[] assignment = new int[requirements.size()];
        Arrays.fill(assignment, -1);
        SearchState state = new SearchState();
        PlanResult plan = findRepeatedAssignment(
                0, order, candidateGroups, requirements, groups, remaining, assignment,
                inventory, grid, crafts, state
        );
        if (plan != null) {
            return plan;
        }
        return new PlanResult(null, state.sawInventorySpaceFailure
                ? Failure.NO_INVENTORY_SPACE
                : Failure.MISSING_INGREDIENTS);
    }

    private static PlanResult findRepeatedAssignment(
            int depth,
            List<Integer> order,
            List<List<Integer>> candidates,
            List<PlacementRequirement> requirements,
            List<ItemGroup> groups,
            int[] remaining,
            int[] assignment,
            List<ItemStack> inventory,
            List<ItemStack> grid,
            int crafts,
            SearchState state
    ) {
        if (depth == order.size()) {
            PlanResult result = buildRepeatedPlan(
                    requirements, groups, assignment, inventory, grid, crafts
            );
            state.sawInventorySpaceFailure |= result.failure() == Failure.NO_INVENTORY_SPACE;
            return result.succeeded() ? result : null;
        }

        int requirement = order.get(depth);
        for (int groupIndex : candidates.get(requirement)) {
            if (remaining[groupIndex] < crafts) {
                continue;
            }
            remaining[groupIndex] -= crafts;
            assignment[requirement] = groupIndex;
            PlanResult result = findRepeatedAssignment(
                    depth + 1, order, candidates, requirements, groups, remaining, assignment,
                    inventory, grid, crafts, state
            );
            if (result != null) {
                return result;
            }
            assignment[requirement] = -1;
            remaining[groupIndex] += crafts;
        }
        return null;
    }

    private static PlanResult buildRepeatedPlan(
            List<PlacementRequirement> requirements,
            List<ItemGroup> groups,
            int[] assignment,
            List<ItemStack> inventory,
            List<ItemStack> grid,
            int crafts
    ) {
        int[] consumedByGroup = new int[groups.size()];
        List<ItemStack> plannedGrid = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            plannedGrid.add(ItemStack.EMPTY);
        }
        for (int index = 0; index < requirements.size(); index++) {
            int groupIndex = assignment[index];
            if (groupIndex < 0) {
                return new PlanResult(null, Failure.MISSING_INGREDIENTS);
            }
            consumedByGroup[groupIndex] += crafts;
            ItemStack placement = groups.get(groupIndex).stack.copy();
            placement.setCount(crafts);
            plannedGrid.set(requirements.get(index).gridSlot(), placement);
        }

        int[] consumedBySource = new int[INVENTORY_SIZE + GRID_SIZE];
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            int remaining = consumedByGroup[groupIndex];
            List<SourceStack> sources = new ArrayList<>(groups.get(groupIndex).sources);
            sources.sort(Comparator.comparingInt(source ->
                    source.index() < INVENTORY_SIZE ? 1 : 0));
            for (SourceStack source : sources) {
                int consumed = Math.min(remaining, source.count());
                consumedBySource[source.index()] += consumed;
                remaining -= consumed;
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining != 0) {
                return new PlanResult(null, Failure.MISSING_INGREDIENTS);
            }
        }

        List<ItemStack> updatedInventory = new ArrayList<>(INVENTORY_SIZE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack remaining = inventory.get(slot).copy();
            remaining.shrink(consumedBySource[slot]);
            updatedInventory.add(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack leftover = grid.get(slot).copy();
            leftover.shrink(consumedBySource[INVENTORY_SIZE + slot]);
            if (!leftover.isEmpty() && !insertIntoInventory(updatedInventory, leftover)) {
                return new PlanResult(null, Failure.NO_INVENTORY_SPACE);
            }
        }

        return new PlanResult(
                new Plan(List.copyOf(plannedGrid), List.copyOf(updatedInventory),
                        requirements.size() * crafts, crafts),
                Failure.NONE
        );
    }

    private static List<ItemGroup> groupSources(List<ItemStack> inventory, List<ItemStack> grid) {
        List<ItemGroup> groups = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < INVENTORY_SIZE + GRID_SIZE; sourceIndex++) {
            ItemStack source = sourceIndex < INVENTORY_SIZE
                    ? inventory.get(sourceIndex)
                    : grid.get(sourceIndex - INVENTORY_SIZE);
            if (source.isEmpty()) {
                continue;
            }

            ItemGroup group = groups.stream()
                    .filter(candidate -> ItemStack.isSameItemSameComponents(candidate.stack, source))
                    .findFirst()
                    .orElse(null);
            if (group == null) {
                ItemStack sample = source.copy();
                sample.setCount(1);
                group = new ItemGroup(sample);
                groups.add(group);
            }
            group.add(new SourceStack(sourceIndex, source.getCount()));
        }
        return groups;
    }

    static PlanResult planRequirements(
            List<PlacementRequirement> requirements,
            List<ItemStack> inventory,
            List<ItemStack> grid
    ) {
        if (requirements.isEmpty() || requirements.size() > GRID_SIZE
                || inventory.size() < INVENTORY_SIZE || grid.size() != GRID_SIZE) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }

        List<ItemStack> sources = new ArrayList<>(INVENTORY_SIZE + GRID_SIZE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            sources.add(inventory.get(slot).copy());
        }
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            sources.add(grid.get(slot).copy());
        }

        List<InventoryUnit> units = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            ItemStack stack = sources.get(sourceIndex);
            if (stack.isEmpty()) {
                continue;
            }
            int availableUnits = Math.min(stack.getCount(), requirements.size());
            for (int unit = 0; unit < availableUnits; unit++) {
                units.add(new InventoryUnit(sourceIndex, stack));
            }
        }

        List<List<Integer>> candidatesByRequirement = new ArrayList<>(requirements.size());
        for (PlacementRequirement requirement : requirements) {
            List<Integer> candidates = new ArrayList<>();
            for (int unit = 0; unit < units.size(); unit++) {
                if (requirement.matches().test(units.get(unit).stack())) {
                    candidates.add(unit);
                }
            }
            // Empty small source stacks first; prefer the old grid on ties.
            // This reuses grid ingredients instead of needlessly returning them
            // to an already-full inventory.
            candidates.sort(Comparator
                    .comparingInt((Integer unit) -> units.get(unit).stack().getCount())
                    .thenComparingInt(unit -> units.get(unit).sourceIndex() >= INVENTORY_SIZE ? 0 : 1));
            candidatesByRequirement.add(candidates);
        }

        int[] unitToRequirement = new int[units.size()];
        Arrays.fill(unitToRequirement, -1);
        List<Integer> matchingOrder = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            matchingOrder.add(index);
        }
        matchingOrder.sort(Comparator.comparingInt(index -> candidatesByRequirement.get(index).size()));

        for (int requirement : matchingOrder) {
            tryAssign(requirement, candidatesByRequirement, unitToRequirement, new boolean[units.size()]);
        }

        int[] requirementToUnit = new int[requirements.size()];
        Arrays.fill(requirementToUnit, -1);
        for (int unit = 0; unit < unitToRequirement.length; unit++) {
            int requirement = unitToRequirement[unit];
            if (requirement >= 0) {
                requirementToUnit[requirement] = unit;
            }
        }

        if (Arrays.stream(requirementToUnit).anyMatch(unit -> unit < 0)) {
            return new PlanResult(null, Failure.MISSING_INGREDIENTS);
        }

        List<ItemStack> plannedGrid = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            plannedGrid.add(ItemStack.EMPTY);
        }
        int[] consumedBySource = new int[sources.size()];
        for (int index = 0; index < requirements.size(); index++) {
            int unit = requirementToUnit[index];
            ItemStack source = sources.get(units.get(unit).sourceIndex());
            ItemStack placed = source.copy();
            placed.setCount(1);
            plannedGrid.set(requirements.get(index).gridSlot(), placed);
            consumedBySource[units.get(unit).sourceIndex()]++;
        }

        List<ItemStack> updatedInventory = new ArrayList<>(INVENTORY_SIZE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack remaining = inventory.get(slot).copy();
            remaining.shrink(consumedBySource[slot]);
            updatedInventory.add(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack source = sources.get(INVENTORY_SIZE + slot);
            ItemStack remaining = source.copy();
            remaining.shrink(consumedBySource[INVENTORY_SIZE + slot]);
            if (!remaining.isEmpty() && !insertIntoInventory(updatedInventory, remaining)) {
                return new PlanResult(null, Failure.NO_INVENTORY_SPACE);
            }
        }

        return new PlanResult(new Plan(List.copyOf(plannedGrid), List.copyOf(updatedInventory),
                requirements.size(), 1),
                Failure.NONE);
    }

    private static boolean insertIntoInventory(List<ItemStack> inventory, ItemStack stack) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            ItemStack existing = inventory.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }

            int capacity = Math.max(0, existing.getMaxStackSize() - existing.getCount());
            int moved = Math.min(capacity, remaining.getCount());
            if (moved > 0) {
                ItemStack merged = existing.copy();
                merged.grow(moved);
                inventory.set(slot, merged);
                remaining.shrink(moved);
            }
        }

        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            if (!inventory.get(slot).isEmpty()) {
                continue;
            }

            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            inventory.set(slot, placed);
            remaining.shrink(moved);
        }

        return remaining.isEmpty();
    }

    private static boolean tryAssign(
            int requirement,
            List<List<Integer>> candidatesByRequirement,
            int[] unitToRequirement,
            boolean[] visitedUnits
    ) {
        for (int unit : candidatesByRequirement.get(requirement)) {
            if (visitedUnits[unit]) {
                continue;
            }
            visitedUnits[unit] = true;
            int displacedRequirement = unitToRequirement[unit];
            if (displacedRequirement < 0
                    || tryAssign(displacedRequirement, candidatesByRequirement, unitToRequirement, visitedUnits)) {
                unitToRequirement[unit] = requirement;
                return true;
            }
        }
        return false;
    }

    enum Failure {
        NONE,
        UNSUPPORTED,
        MISSING_INGREDIENTS,
        NO_INVENTORY_SPACE
    }

    record PlanResult(Plan plan, Failure failure) {
        boolean succeeded() {
            return this.plan != null && this.failure == Failure.NONE;
        }
    }

    record Plan(List<ItemStack> grid, List<ItemStack> inventory, int required, int crafts) {
    }

    record PlacementRequirement(int gridSlot, Predicate<ItemStack> matches) {
    }

    private record InventoryUnit(int sourceIndex, ItemStack stack) {
    }

    private record SourceStack(int index, int count) {
    }

    private static final class ItemGroup {
        private final ItemStack stack;
        private final List<SourceStack> sources = new ArrayList<>();
        private int count;

        private ItemGroup(ItemStack stack) {
            this.stack = stack;
        }

        private void add(SourceStack source) {
            this.sources.add(source);
            this.count += source.count();
        }

        private int totalCount() {
            return this.count;
        }

        private int slotCapacity() {
            return Math.min(this.count, this.stack.getMaxStackSize());
        }

        private boolean hasGridSource() {
            return this.sources.stream().anyMatch(source -> source.index() >= INVENTORY_SIZE);
        }
    }

    private static final class SearchState {
        private boolean sawInventorySpaceFailure;
    }
}
