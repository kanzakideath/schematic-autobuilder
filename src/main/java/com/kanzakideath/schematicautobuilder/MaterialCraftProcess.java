package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class MaterialCraftProcess {

    public interface CraftCallback {
        void finished(CraftResult result);
    }

    public record CraftResult(boolean crafted, int craftedCount, String message) {
    }

    private enum State {
        IDLE,
        WALKING,
        OPENING,
        CRAFTING
    }

    private static State state = State.IDLE;
    private static Set<Item> neededItems = Set.of();
    private static BlockPos targetCraftingTable;
    private static CraftCallback callback;
    private static int clickCooldown;
    private static int pathCooldown;
    private static int openAttempts;
    private static int craftedCount;
    private static int craftAttempts;
    private static boolean pendingTakeResult;
    private static String status = "Idle";

    private MaterialCraftProcess() {}

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static String status() {
        return status;
    }

    public static boolean start(CraftCallback craftCallback) {
        return start(craftCallback, BaritoneBridge.currentNeededBuildItems());
    }

    public static boolean start(CraftCallback craftCallback, Set<Item> neededOverride) {
        if (state != State.IDLE) {
            status = "Material crafting already running";
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return false;
        }
        Set<Item> needed = neededOverride == null ? Set.of() : Set.copyOf(neededOverride);
        if (needed.isEmpty() || MaterialRecipeHelper.findCraftableCraftingRecipe(minecraft, minecraft.player, needed) == null) {
            status = "No craftable needed material";
            return false;
        }
        neededItems = needed;
        callback = craftCallback;
        targetCraftingTable = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        craftedCount = 0;
        craftAttempts = 0;
        pendingTakeResult = false;
        state = State.WALKING;
        status = "Preparing material crafting";
        return true;
    }

    public static void stop(String reason) {
        CraftCallback previous = callback;
        state = State.IDLE;
        neededItems = Set.of();
        targetCraftingTable = null;
        callback = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        craftedCount = 0;
        craftAttempts = 0;
        pendingTakeResult = false;
        status = reason == null ? "Idle" : reason;
        if (previous != null && "Paused".equals(reason)) {
            previous.finished(new CraftResult(false, 0, status));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (state == State.IDLE || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player.containerMenu instanceof CraftingMenu craftingMenu) {
            state = State.CRAFTING;
            craftTick(minecraft, player, craftingMenu);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (targetCraftingTable == null || !isCraftingTable(minecraft, targetCraftingTable)) {
            targetCraftingTable = nearestCraftingTable(minecraft, player);
            if (targetCraftingTable == null) {
                finish("No crafting table found for material auto craft", ChatFormatting.YELLOW, craftedCount > 0);
                return;
            }
        }
        double distance = player.position().distanceTo(Vec3.atCenterOf(targetCraftingTable));
        if (distance > 4.5D) {
            state = State.WALKING;
            status = "Walking to crafting table";
            if (pathCooldown <= 0) {
                BaritoneBridge.pathToBlock(targetCraftingTable);
                pathCooldown = 20;
            } else {
                pathCooldown--;
            }
            return;
        }
        state = State.OPENING;
        status = "Opening crafting table";
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        clickCooldown = 8;
        openAttempts++;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(targetCraftingTable), Direction.UP, targetCraftingTable, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (openAttempts > 30) {
            finish("Could not open crafting table", ChatFormatting.YELLOW, craftedCount > 0);
        }
    }

    private static void craftTick(Minecraft minecraft, LocalPlayer player, CraftingMenu menu) {
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        if (pendingTakeResult) {
            ItemStack result = menu.getSlot(0).getItem();
            if (!result.isEmpty()) {
                minecraft.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                craftedCount++;
                pendingTakeResult = false;
                clickCooldown = 5;
                status = "Crafted material stack(s): " + craftedCount;
                return;
            }
            pendingTakeResult = false;
        }
        if (craftAttempts >= 32) {
            player.closeContainer();
            finish("Material auto craft attempt limit reached", craftedCount > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW, craftedCount > 0);
            return;
        }
        RecipeDisplayEntry entry = MaterialRecipeHelper.findCraftableCraftingRecipe(minecraft, player, neededItems);
        if (entry == null) {
            player.closeContainer();
            finish(craftedCount > 0 ? "Crafted " + craftedCount + " material stack(s)" : "No more craftable material recipes", craftedCount > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW, craftedCount > 0);
            return;
        }
        minecraft.gameMode.handlePlaceRecipe(menu.containerId, entry.id(), true);
        pendingTakeResult = true;
        craftAttempts++;
        clickCooldown = 4;
        status = "Auto crafting needed material";
    }

    private static void finish(String message, ChatFormatting color, boolean crafted) {
        CraftCallback done = callback;
        int count = craftedCount;
        state = State.IDLE;
        neededItems = Set.of();
        targetCraftingTable = null;
        callback = null;
        clickCooldown = 0;
        pathCooldown = 0;
        openAttempts = 0;
        craftedCount = 0;
        craftAttempts = 0;
        pendingTakeResult = false;
        status = message;
        AutoBuildController.message(message, color);
        if (done != null) {
            done.finished(new CraftResult(crafted, count, message));
        }
    }

    private static BlockPos nearestCraftingTable(Minecraft minecraft, LocalPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 24;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!minecraft.level.isLoaded(pos) || !isCraftingTable(minecraft, pos)) {
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

    private static boolean isCraftingTable(Minecraft minecraft, BlockPos pos) {
        if (minecraft == null || minecraft.level == null || pos == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return state.is(Blocks.CRAFTING_TABLE);
    }
}
