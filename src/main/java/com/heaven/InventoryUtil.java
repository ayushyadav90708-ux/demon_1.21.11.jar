package com.heaven;

import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Inventory helpers that use only normal hotbar selection and slot swaps. */
public final class InventoryUtil {
    private InventoryUtil() {}

    public enum Prep { READY, PENDING, MISSING }

    public static int findHotbar(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) {
                return i;
            }
        }
        return -1;
    }

    public static int findMain(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) {
                return i;
            }
        }
        return -1;
    }

    public static int count(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) {
                total += s.getCount();
            }
        }
        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty() && pred.test(off)) {
            total += off.getCount();
        }
        return total;
    }

    public static boolean has(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        return count(p, pred) > 0;
    }

    /**
     * Makes sure a matching stack is in the selected hotbar slot.
     * READY = in main hand now, PENDING = a switch/swap was just issued (try next tick),
     * MISSING = nothing matching in the inventory.
     */
    public static Prep prepare(MinecraftClient client, Predicate<ItemStack> pred, boolean allowInventorySwap) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.interactionManager == null) {
            return Prep.MISSING;
        }
        int selected = p.getInventory().getSelectedSlot();
        if (pred.test(p.getInventory().getStack(selected)) && !p.getInventory().getStack(selected).isEmpty()) {
            return Prep.READY;
        }
        int hotbar = findHotbar(p, pred);
        if (hotbar >= 0) {
            p.getInventory().setSelectedSlot(hotbar);
            return Prep.PENDING;
        }
        if (allowInventorySwap) {
            int main = findMain(p, pred);
            if (main >= 0) {
                client.interactionManager.clickSlot(p.playerScreenHandler.syncId, main, selected,
                        SlotActionType.SWAP, p);
                return Prep.PENDING;
            }
        }
        return Prep.MISSING;
    }
}
