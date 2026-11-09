package dev.dragongamer112.polycraftingtable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Objects;
import java.util.stream.Collectors;

public final class CraftingRestrictions {
    private static CraftingRestrictionPolicy policy;

    private CraftingRestrictions() {
    }

    static void initialize() {
        policy = CraftingRestrictionConfig.load(
                FabricLoader.getInstance().getConfigDir().resolve("polycraftingtable.json")
        );
        PolyCraftingTableMod.LOGGER.info("Vanilla crafting restrictions: {}. Restart to apply config edits.",
                policy.summary());
        ServerLifecycleEvents.SERVER_STARTED.register(CraftingRestrictions::validateRules);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                validateRules(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RecipeBookFilter.clearWarnings());
    }

    public static CraftingRestrictionPolicy policy() {
        return Objects.requireNonNull(policy, "Crafting restrictions have not been initialized");
    }

    public static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void validateRules(MinecraftServer server) {
        var itemIds = BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).collect(Collectors.toSet());
        var recipeIds = server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == RecipeType.CRAFTING)
                .map(holder -> holder.id().identifier().toString()).collect(Collectors.toSet());
        for (String rule : policy().unknownRules(itemIds, recipeIds)) {
            PolyCraftingTableMod.LOGGER.warn("Crafting config references unknown {}. Rule retained; "
                    + "check installed mods and enabled datapacks.", rule);
        }
    }
}
