package dev.dragongamer112.polycraftingtable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class PolyCraftingTableMod implements ModInitializer {
    public static final String MOD_ID = "polycraftingtable";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier TABLE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "polymer_crafting_table");
    static final Component TABLE_NAME = Component.translatableWithFallback(
            "block.polycraftingtable.polymer_crafting_table", "Polymer Crafting Table"
    );
    public static final Identifier POLYDEX_ID = Identifier.fromNamespaceAndPath(MOD_ID, "polydex");
    static final Component POLYDEX_NAME = Component.translatableWithFallback(
            "item.polycraftingtable.polydex", "Polydex"
    );
    static final AttachmentType<List<Identifier>> RECENT_ITEM_IDS = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(MOD_ID, "recent_item_ids"),
            Identifier.CODEC.listOf()
    );
    static final AttachmentType<Map<String, String>> LAST_FILLED_RECIPE_IDS = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(MOD_ID, "last_filled_recipe_ids"),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
    );
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
    public static final Item POLYDEX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            POLYDEX_ID,
            new PolydexItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, POLYDEX_ID)))
    );

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                OpenPlayerInventoryPayload.TYPE,
                OpenPlayerInventoryPayload.CODEC
        );
        CraftingRestrictions.initialize();
        PolydexReturnSession.register();
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        // Use ITEM_NAME, not CUSTOM_NAME: anvil names must still take precedence.
        // The fallback also works on vanilla clients without our language file.
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
                TABLE_ITEM, builder -> builder.set(DataComponents.ITEM_NAME, TABLE_NAME)
        ));
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
                POLYDEX_ITEM, builder -> builder.set(DataComponents.ITEM_NAME, POLYDEX_NAME)
        ));
        CreativeModeTabEvents.modifyOutputEvent(
                CreativeModeTabs.FUNCTIONAL_BLOCKS
        ).register(entries -> entries.accept(TABLE_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(
                CreativeModeTabs.TOOLS_AND_UTILITIES
        ).register(entries -> entries.accept(POLYDEX_ITEM));

        LOGGER.info("Polymer Crafting Table initialized.");
    }

    static void openPolydexMenu(ServerPlayer player, BlockPos pos) {
        PolydexReturnSession.openMain(player, pos);
    }

    static void openPolydexFromHand(ServerPlayer player) {
        PolydexReturnSession.openFromHand(player);
    }
}
