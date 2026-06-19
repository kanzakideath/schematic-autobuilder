package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Set;

public final class CreativeMaterialSupplier {
    private static final int MAX_SUPPLIED_PER_PASS = 6;
    private static final int RESERVED_EMPTY_SLOTS = 4;

    private CreativeMaterialSupplier() {}

    public static int supplyNeeded(Minecraft minecraft, Set<Item> neededItems) {
        return supplyNeeded(minecraft, neededItems, List.of());
    }

    public static int supplyNeeded(Minecraft minecraft, Set<Item> neededItems, List<Item> scaffoldItems) {
        Set<Item> needed = neededItems == null ? Set.of() : neededItems;
        List<Item> scaffolds = scaffoldItems == null ? List.of() : scaffoldItems;
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null || (needed.isEmpty() && scaffolds.isEmpty())) {
            return 0;
        }
        LocalPlayer player = minecraft.player;
        if (!player.isCreative()) {
            return 0;
        }
        int supplied = 0;
        Set<Integer> reservedSlots = new HashSet<>();
        Set<Item> protectedItems = new HashSet<>(needed);
        protectedItems.addAll(scaffolds);
        List<Item> orderedItems = new ArrayList<>(needed);
        orderedItems.sort(Comparator.comparing(Item::toString));
        for (Item item : orderedItems) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            if (hasUsableStack(player, item)) {
                continue;
            }
            if (supplied >= MAX_SUPPLIED_PER_PASS) {
                break;
            }
            int inventorySlot = preferredSlot(player, item, protectedItems, reservedSlots);
            if (inventorySlot < 0) {
                continue;
            }
            ItemStack stack = item.getDefaultInstance().copy();
            stack.setCount(stack.getMaxStackSize());
            if (setCreativeSlot(minecraft, player, inventorySlot, stack)) {
                reservedSlots.add(inventorySlot);
                supplied++;
            }
        }
        if (supplied < MAX_SUPPLIED_PER_PASS && !hasAnyUsableStack(player, scaffolds)) {
            for (Item scaffold : scaffolds) {
                if (scaffold == null || scaffold == Items.AIR) {
                    continue;
                }
                int inventorySlot = preferredSlot(player, scaffold, protectedItems, reservedSlots);
                if (inventorySlot < 0) {
                    continue;
                }
                ItemStack stack = scaffold.getDefaultInstance().copy();
                stack.setCount(stack.getMaxStackSize());
                if (setCreativeSlot(minecraft, player, inventorySlot, stack)) {
                    reservedSlots.add(inventorySlot);
                    supplied++;
                }
                break;
            }
        }
        return supplied;
    }

    private static int preferredSlot(LocalPlayer player, Item item, Set<Item> protectedItems, Set<Integer> reservedSlots) {
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (reservedSlots.contains(i)) {
                continue;
            }
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
            if (!stack.isEmpty() && stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                return i;
            }
        }
        if (emptySlots(player, reservedSlots) > RESERVED_EMPTY_SLOTS) {
            for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
                if (!reservedSlots.contains(i) && player.getInventory().getNonEquipmentItems().get(i).isEmpty()) {
                    return i;
                }
            }
        }
        int replaceable = replacementSlot(player, protectedItems, reservedSlots, 9, player.getInventory().getNonEquipmentItems().size());
        if (replaceable >= 0) {
            return replaceable;
        }
        replaceable = replacementSlot(player, protectedItems, reservedSlots, 0, 9);
        if (replaceable >= 0) {
            return replaceable;
        }
        return -1;
    }

    private static boolean hasAnyUsableStack(LocalPlayer player, Collection<Item> items) {
        for (Item item : items) {
            if (item != null && item != Items.AIR && hasUsableStack(player, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsableStack(LocalPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static int replacementSlot(LocalPlayer player, Set<Item> neededItems, Set<Integer> reservedSlots, int start, int end) {
        int limit = Math.min(end, player.getInventory().getNonEquipmentItems().size());
        for (int i = start; i < limit; i++) {
            if (reservedSlots.contains(i)) {
                continue;
            }
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
            if (stack.isEmpty() || !neededItems.contains(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private static int emptySlots(LocalPlayer player, Set<Integer> reservedSlots) {
        int empty = 0;
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (!reservedSlots.contains(i) && player.getInventory().getNonEquipmentItems().get(i).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    private static boolean setCreativeSlot(Minecraft minecraft, LocalPlayer player, int inventorySlot, ItemStack stack) {
        try {
            Method method = minecraft.gameMode.getClass().getMethod("handleCreativeModeItemAdd", ItemStack.class, int.class);
            method.invoke(minecraft.gameMode, stack.copy(), inventorySlotToMenuSlot(inventorySlot));
            player.getInventory().getNonEquipmentItems().set(inventorySlot, stack.copy());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static int inventorySlotToMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
