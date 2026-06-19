package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
        for (Item item : neededItems) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            int inventorySlot = preferredSlot(player, item);
            if (inventorySlot < 0) {
                continue;
            }
            ItemStack stack = item.getDefaultInstance().copy();
            stack.setCount(stack.getMaxStackSize());
            if (setCreativeSlot(minecraft, player, inventorySlot, stack)) {
                supplied++;
            }
        }
        return supplied;
    }

    private static int preferredSlot(LocalPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
            if (!stack.isEmpty() && stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            if (player.getInventory().getNonEquipmentItems().get(i).isEmpty()) {
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
