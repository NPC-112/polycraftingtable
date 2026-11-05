package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class PolymerCraftingTableBlock extends Block implements PolymerBlock {
    public PolymerCraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            return PolyCraftingTableMod.openRecipeLookup(serverPlayer, pos, hit);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            new PolymerCraftingTableGui(serverPlayer, pos);
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return Blocks.CRAFTING_TABLE.defaultBlockState();
    }

    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        return Blocks.CRAFTING_TABLE.defaultBlockState();
    }
}
