package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class MaterialSmeltProcess {

    public interface SmeltCallback {
        void finished(SmeltResult result);
    }

    public record SmeltResult(boolean smelted, int outputTaken, String message) {
    }

    private enum State {
        IDLE,
        WALKING,
        OPENING,
        SMELTING
    }

    private record SmeltPlan(Item input, Item output) {
    }

    private static State state = State.IDLE;
    private static SmeltCallback callback;
    private static SmeltPlan plan;
    private static Set<Item> neededItems = Set.of();
    private static BlockPos targetFurnace;
    private static int clickCooldown;
    private static int pathCooldown;
    private static int openAttempts;
    private static int waitTicks;
    private static int outputTaken;
    private static String status = "Idle";

    private MaterialSmeltProcess() {}

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static String status() {
        return status;
    }

    public static boolean start(SmeltCallback smeltCallback) {
        if (state != State.IDLE) {
            status = "Material smelting already running";
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }
        Set<Item> needed = BaritoneBridge.currentNeededBuildItems();
        SmeltPlan selected = findPlan(minecraft.player, needed);
        if (selected == null) {
            status = "No smeltable needed material";
            return false;
        }
        neededItems = needed;
        plan = selected;
        callback = smeltCallback;
        targetFurnace = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        waitTicks = 0;
        outputTaken = 0;
        state = State.WALKING;
        status = "Preparing smelting: " + selected.input + " -> " + selected.output;
        return true;
    }

    public static void stop(String reason) {
        SmeltCallback previous = callback;
        state = State.IDLE;
        callback = null;
        plan = null;
        neededItems = Set.of();
        targetFurnace = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        waitTicks = 0;
        outputTaken = 0;
        status = reason == null ? "Idle" : reason;
        if (previous != null && "Paused".equals(reason)) {
            previous.finished(new SmeltResult(false, 0, status));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (state == State.IDLE || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null || plan == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player.containerMenu instanceof AbstractFurnaceMenu furnaceMenu) {
            state = State.SMELTING;
            smeltTick(minecraft, player, furnaceMenu);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (targetFurnace == null || !isFurnace(minecraft, targetFurnace)) {
            targetFurnace = nearestFurnace(minecraft, player);
            if (targetFurnace == null) {
                finish("No furnace found for material smelting", ChatFormatting.YELLOW, outputTaken > 0);
                return;
            }
        }
        double distance = player.position().distanceTo(Vec3.atCenterOf(targetFurnace));
        if (distance > 4.5D) {
            state = State.WALKING;
            status = "Walking to furnace";
            if (pathCooldown <= 0) {
                BaritoneBridge.pathToBlock(targetFurnace);
                pathCooldown = 20;
            } else {
                pathCooldown--;
            }
            return;
        }
        state = State.OPENING;
        status = "Opening furnace";
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        clickCooldown = 8;
        openAttempts++;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(targetFurnace), Direction.UP, targetFurnace, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (openAttempts > 30) {
            finish("Could not open furnace", ChatFormatting.YELLOW, outputTaken > 0);
        }
    }

    private static void smeltTick(Minecraft minecraft, LocalPlayer player, AbstractFurnaceMenu menu) {
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        Slot resultSlot = menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT);
        ItemStack result = resultSlot.getItem();
        if (!result.isEmpty()) {
            int count = result.getCount();
            minecraft.gameMode.handleContainerInput(menu.containerId, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
            outputTaken += count;
            clickCooldown = 5;
            waitTicks = 0;
            status = "Smelted material item(s): " + outputTaken;
            if (MaterialRecipeHelper.findCraftableCraftingRecipe(minecraft, player, neededItems) != null || hasNeededOutput(player)) {
                player.closeContainer();
                finish("Smelted " + outputTaken + " material item(s)", ChatFormatting.GREEN, true);
            }
            return;
        }
        if (menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().isEmpty()) {
            int source = firstMenuSlotWith(menu, plan.input);
            if (source == -1) {
                player.closeContainer();
                finish(outputTaken > 0 ? "Smelted " + outputTaken + " material item(s)" : "No smelting input available", outputTaken > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW, outputTaken > 0);
                return;
            }
            moveStackToSlot(minecraft, player, menu, source, AbstractFurnaceMenu.INGREDIENT_SLOT);
            clickCooldown = 5;
            return;
        }
        if (menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem().isEmpty()) {
            int fuel = firstFuelSlot(menu);
            if (fuel == -1) {
                player.closeContainer();
                finish(outputTaken > 0 ? "Smelted " + outputTaken + " material item(s)" : "No fuel for material smelting", outputTaken > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW, outputTaken > 0);
                return;
            }
            moveStackToSlot(minecraft, player, menu, fuel, AbstractFurnaceMenu.FUEL_SLOT);
            clickCooldown = 5;
            return;
        }
        waitTicks++;
        status = "Smelting material: " + waitTicks + " ticks";
        if (waitTicks > 2000) {
            player.closeContainer();
            finish(outputTaken > 0 ? "Smelted " + outputTaken + " material item(s)" : "Material smelting timed out", outputTaken > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW, outputTaken > 0);
        }
    }

    private static void moveStackToSlot(Minecraft minecraft, LocalPlayer player, AbstractFurnaceMenu menu, int sourceSlot, int targetSlot) {
        minecraft.gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);
        minecraft.gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            minecraft.gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);
        }
    }

    private static int firstMenuSlotWith(AbstractFurnaceMenu menu, Item item) {
        for (int i = AbstractFurnaceMenu.SLOT_COUNT; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstFuelSlot(AbstractFurnaceMenu menu) {
        for (int i = AbstractFurnaceMenu.SLOT_COUNT; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (isFuel(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.COAL)
                || stack.is(Items.CHARCOAL)
                || stack.is(Items.COAL_BLOCK)
                || stack.is(Items.LAVA_BUCKET));
    }

    private static boolean hasNeededOutput(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && neededItems.contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static SmeltPlan findPlan(LocalPlayer player, Set<Item> needed) {
        if (needed.isEmpty()) {
            return null;
        }
        if ((needed.contains(Items.STONE) || needed.contains(Items.STONE_BRICKS) || needed.contains(Items.STONE_BRICK_SLAB)) && hasItem(player, Items.COBBLESTONE)) {
            return new SmeltPlan(Items.COBBLESTONE, Items.STONE);
        }
        if ((needed.contains(Items.SMOOTH_STONE) || needed.contains(Items.SMOOTH_STONE_SLAB)) && hasItem(player, Items.STONE)) {
            return new SmeltPlan(Items.STONE, Items.SMOOTH_STONE);
        }
        if ((needed.contains(Items.SMOOTH_STONE) || needed.contains(Items.SMOOTH_STONE_SLAB)) && hasItem(player, Items.COBBLESTONE)) {
            return new SmeltPlan(Items.COBBLESTONE, Items.STONE);
        }
        if (needed.contains(Items.GLASS) && hasItem(player, Items.SAND)) {
            return new SmeltPlan(Items.SAND, Items.GLASS);
        }
        return null;
    }

    private static boolean hasItem(LocalPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void finish(String message, ChatFormatting color, boolean smelted) {
        SmeltCallback done = callback;
        int count = outputTaken;
        state = State.IDLE;
        callback = null;
        plan = null;
        neededItems = Set.of();
        targetFurnace = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        waitTicks = 0;
        outputTaken = 0;
        status = message;
        AutoBuildController.message(message, color);
        if (done != null) {
            done.finished(new SmeltResult(smelted, count, message));
        }
    }

    private static BlockPos nearestFurnace(Minecraft minecraft, LocalPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 24;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!minecraft.level.isLoaded(pos) || !isFurnace(minecraft, pos)) {
                        continue;
                    }
                    double distance = player.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean isFurnace(Minecraft minecraft, BlockPos pos) {
        if (minecraft == null || minecraft.level == null || pos == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return state.is(Blocks.FURNACE);
    }
}
