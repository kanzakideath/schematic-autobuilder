package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.lang.reflect.Method;
import java.util.Set;

public final class CreativeMaterialSupplier {

    private CreativeMaterialSupplier() {}

    public static int supplyNeeded(Minecraft minecraft, Set<Item> neededItems) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null || neededItems == null || neededItems.isEmpty()) {
            return 0;
        }
        LocalPlayer player = minecraft.player;
        if (!player.isCreative()) {
            return 0;
        }
        int supplied = 0;
        Set<Integer> reservedSlots = new HashSet<>();
        for (Item item : neededItems) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            int inventorySlot = preferredSlot(player, item, neededItems, reservedSlots);
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
        return supplied;
    }

    private static int preferredSlot(LocalPlayer player, Item item, Set<Item> neededItems, Set<Integer> reservedSlots) {
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (reservedSlots.contains(i)) {
                continue;
            }
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
            if (!stack.isEmpty() && stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (!reservedSlots.contains(i) && player.getInventory().getNonEquipmentItems().get(i).isEmpty()) {
                return i;
            }
        }
        int replaceable = replacementSlot(player, neededItems, reservedSlots, 9, player.getInventory().getNonEquipmentItems().size());
        if (replaceable >= 0) {
            return replaceable;
        }
        replaceable = replacementSlot(player, neededItems, reservedSlots, 0, 9);
        if (replaceable >= 0) {
            return replaceable;
        }
        replaceable = anySlot(player, reservedSlots, 9, player.getInventory().getNonEquipmentItems().size());
        if (replaceable >= 0) {
            return replaceable;
        }
        return anySlot(player, reservedSlots, 0, 9);
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

    private static int anySlot(LocalPlayer player, Set<Integer> reservedSlots, int start, int end) {
        int limit = Math.min(end, player.getInventory().getNonEquipmentItems().size());
        for (int i = start; i < limit; i++) {
            if (!reservedSlots.contains(i)) {
                return i;
            }
        }
        return -1;
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
