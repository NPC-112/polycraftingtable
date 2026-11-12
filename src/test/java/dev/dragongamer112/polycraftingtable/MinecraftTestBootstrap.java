package dev.dragongamer112.polycraftingtable;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/** Initializes the minimum shared Minecraft registry state needed by item-stack tests. */
final class MinecraftTestBootstrap {
    private static boolean bootstrapped;

    private MinecraftTestBootstrap() {
    }

    static synchronized void initialize() {
        if (bootstrapped) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var stackable = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        var unstackable = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build();
        for (Item item : List.of(
                Items.OAK_PLANKS, Items.GLASS, Items.COBBLESTONE, Items.STICK
        )) {
            item.builtInRegistryHolder().bindComponents(stackable);
        }
        Items.IRON_PICKAXE.builtInRegistryHolder().bindComponents(unstackable);
        bootstrapped = true;
    }
}
