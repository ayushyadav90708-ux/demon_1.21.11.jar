package com.heaven;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Eats food from the inventory when hunger is low, and stops once mostly restored. */
public class FoodManager {
    private static final int START_AT = 12;
    private static final int STOP_AT = 19;
    private static final int MAX_EAT_TICKS = 400;

    private final HeavenClient heaven;
    private boolean eating;
    private int eatTicks;
    private int cooldown;
    private boolean noFood;

    public FoodManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    private static boolean isSafeFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) {
            return false;
        }
        Item item = stack.getItem();
        return item != Items.ROTTEN_FLESH && item != Items.SPIDER_EYE && item != Items.POISONOUS_POTATO
                && item != Items.PUFFERFISH && item != Items.CHORUS_FRUIT && item != Items.SUSPICIOUS_STEW
                && item != Items.CHICKEN && item != Items.GOLDEN_APPLE && item != Items.ENCHANTED_GOLDEN_APPLE;
    }

    /** Picks the most nourishing safe food: prefers what is already held/in the hotbar. */
    private static java.util.function.Predicate<ItemStack> bestFoodPredicate(ClientPlayerEntity p) {
        int best = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isSafeFood(s)) {
                FoodComponent f = s.get(DataComponentTypes.FOOD);
                if (f != null && f.nutrition() > best) {
                    best = f.nutrition();
                }
            }
        }
        final int target = best;
        return s -> {
            if (!isSafeFood(s)) {
                return false;
            }
            FoodComponent f = s.get(DataComponentTypes.FOOD);
            return f != null && f.nutrition() == target;
        };
    }

    /** @return true while Heaven is busy eating (movement should pause). */
    public boolean tick(MinecraftClient client, InputController in) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.interactionManager == null || !heaven.config().autoFood) {
            eating = false;
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        int food = p.getHungerManager().getFoodLevel();

        if (eating) {
            eatTicks++;
            if (food >= STOP_AT || eatTicks > MAX_EAT_TICKS || p.isGliding()) {
                eating = false;
                return false;
            }
        } else if (food > START_AT || cooldown > 0 || p.isGliding()) {
            return false;
        }

        InventoryUtil.Prep prep = InventoryUtil.prepare(client, bestFoodPredicate(p), true);
        if (prep == InventoryUtil.Prep.MISSING) {
            if (!noFood) {
                Msg.error("Hungry, but there is no safe food in your inventory.");
            }
            noFood = true;
            eating = false;
            cooldown = 200;
            return false;
        }
        noFood = false;
        eating = true;
        if (prep == InventoryUtil.Prep.PENDING) {
            return true;
        }
        if (!p.isUsingItem()) {
            client.interactionManager.interactItem(p, Hand.MAIN_HAND);
        }
        in.use(true);
        return true;
    }

    public boolean isEating() { return eating; }

    public String status() {
        if (!heaven.config().autoFood) {
            return "Off";
        }
        if (eating) {
            return "Eating";
        }
        return noFood ? "No food" : "Auto";
    }

    public void reset() {
        eating = false;
        eatTicks = 0;
        cooldown = 0;
        noFood = false;
    }
}
