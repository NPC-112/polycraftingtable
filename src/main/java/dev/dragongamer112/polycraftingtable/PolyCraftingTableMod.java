package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexCategory;
import eu.pb4.polydex.api.v1.recipe.PolydexPageUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PolyCraftingTableMod implements ModInitializer {
    public static final String MOD_ID = "polycraftingtable";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier TABLE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "polymer_crafting_table");
    public static final Block TABLE = Registry.register(
            BuiltInRegistries.BLOCK,
            TABLE_ID,
            new PolymerCraftingTableBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
                            .setId(ResourceKey.create(Registries.BLOCK, TABLE_ID))
            )
    );
    public static final Item TABLE_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            TABLE_ID,
            new PolymerBlockItem(
                    TABLE,
                    new Item.Properties().setId(ResourceKey.create(Registries.ITEM, TABLE_ID)),
                    Items.CRAFTING_TABLE
            )
    );

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(
                ResourceKey.create(Registries.CREATIVE_MODE_TAB, CreativeModeTabs.FUNCTIONAL_BLOCKS)
        ).register(entries -> entries.accept(TABLE_ITEM));

        LOGGER.info("Polymer Crafting Table initialized.");
    }

    static InteractionResult openRecipeLookup(ServerPlayer player, BlockPos pos, BlockHitResult hit) {
        if (PolydexPageUtils.openCategoryUi(player, PolydexCategory.CRAFTING, null)) {
            return InteractionResult.SUCCESS_SERVER;
        }

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Polydex has no crafting recipes available."),
                true
        );
        return InteractionResult.SUCCESS_SERVER;
    }
}
