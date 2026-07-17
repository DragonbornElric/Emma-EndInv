package com.emma.endinv.folia.adapter;

import com.emma.endinv.autopick.events.IBlockBreakEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.event.block.BlockBreakEvent;

public class FoliaBlockBreakAdapter implements IBlockBreakEvent {

    private final BlockBreakEvent event;

    public FoliaBlockBreakAdapter(BlockBreakEvent event) { this.event = event; }

    @Override
    public LevelAccessor getLevel() {
        return ((CraftWorld) event.getBlock().getWorld()).getHandle();
    }

    @Override
    public BlockPos getPos() {
        return new BlockPos(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
    }

    @Override
    public BlockState getState() {
        return ((CraftWorld) event.getBlock().getWorld()).getHandle().getBlockState(getPos());
    }

    @Override
    public Player getPlayer() {
        return ((CraftPlayer) event.getPlayer()).getHandle();
    }

    @Override
    public int getExpToDrop() { return event.getExpToDrop(); }

    @Override
    public void setExpToDrop(int exp) { event.setExpToDrop(exp); }

    @Override
    public void setCanceled(boolean canceled) {
        if (canceled) event.setDropItems(false);
    }
}
