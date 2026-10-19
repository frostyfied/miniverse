package dev.frost.miniverse.minigame.core.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Opt-in interface for minigames that disallow players from dropping items from their inventory
 * (e.g. to prevent sharing items between teammates in competitive modes).
 */
public interface ItemDropProtectionAware {
    default boolean shouldCancelItemDrop(ServerPlayerEntity player, ItemStack stack) {
        return true;
    }
}
