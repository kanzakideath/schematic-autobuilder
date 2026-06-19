package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class MaterialChestProcess {

    public interface FetchCallback {
        void finished(FetchResult result);
    }

    public static final class FetchResult {
        private final boolean tookMaterials;
        private final int stacksTaken;
        private final boolean inventoryFull;
        private final String message;

        private FetchResult(boolean tookMaterials, int stacksTaken, boolean inventoryFull, String message) {
            this.tookMaterials = tookMaterials;
            this.stacksTaken = stacksTaken;
            this.inventoryFull = inventoryFull;
            this.message = message;
        }

        public boolean tookMaterials() {
            return tookMaterials;
        }

        public int stacksTaken() {
            return stacksTaken;
        }

        public boolean inventoryFull() {
            return inventoryFull;
        }

        public String message() {
            return message;
        }
    }

    private enum State {
        IDLE,
        WALKING,
        OPENING,
        TAKING
    }

    private static State state = State.IDLE;
    private static List<BlockPos> targets = List.of();
    private static BlockPos target;
    private static FetchCallback callback;
    private static int targetIndex;
    private static int openAttempts;
    private static int clickCooldown;
    private static int pathCooldown;
    private static int currentChestTaken;
    private static int totalTakenStacks;
    private static Set<Item> neededItems = Set.of();
    private static String status = "Idle";

    private MaterialChestProcess() {}

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static String status() {
        return status;
    }

    public static boolean start(FetchCallback fetchCallback) {
        if (state != State.IDLE) {
            status = "Material fetch already running";
            return false;
        }
        List<BlockPos> chests = sortedMaterialChests();
        if (chests.isEmpty()) {
            status = "No material chest registered";
            AutoBuildController.message(status, ChatFormatting.YELLOW);
            return false;
        }
        targets = chests;
        callback = fetchCallback;
        neededItems = BaritoneBridge.currentNeededBuildItems();
        targetIndex = 0;
        totalTakenStacks = 0;
        selectTarget();
        state = State.WALKING;
        status = neededItems.isEmpty()
                ? "Going to material chest " + shortPos(target)
                : "Going to material chest " + shortPos(target) + " for " + neededItems.size() + " needed item type(s)";
        AutoBuildController.message(status, ChatFormatting.AQUA);
        return true;
    }

    public static void stop(String reason) {
        FetchCallback previousCallback = callback;
        state = State.IDLE;
        targets = List.of();
        target = null;
        callback = null;
        targetIndex = 0;
        openAttempts = 0;
        clickCooldown = 0;
        pathCooldown = 0;
        currentChestTaken = 0;
        totalTakenStacks = 0;
        neededItems = Set.of();
        status = reason == null ? "Idle" : reason;
        if (previousCallback != null && "Paused".equals(reason)) {
            previousCallback.finished(new FetchResult(false, 0, false, status));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (state == State.IDLE || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (target == null) {
            finish("No target chest", ChatFormatting.YELLOW);
            return;
        }
        if (player.containerMenu instanceof ChestMenu chestMenu) {
            state = State.TAKING;
            takeMaterials(minecraft, player, chestMenu);
            return;
        }

        double distance = player.position().distanceTo(Vec3.atCenterOf(target));
        if (distance > 4.5D) {
            state = State.WALKING;
            status = "Walking to material chest " + shortPos(target);
            if (pathCooldown <= 0) {
                BaritoneBridge.pathToChest(target);
                pathCooldown = 20;
            } else {
                pathCooldown--;
            }
            return;
        }

        if (minecraft.level.isLoaded(target) && !isChest(minecraft, target)) {
            skipCurrentChest("Registered chest is not present: " + shortPos(target));
            return;
        }

        state = State.OPENING;
        status = "Opening material chest " + shortPos(target);
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        clickCooldown = 8;
        openAttempts++;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (openAttempts > 30) {
            skipCurrentChest("Could not open material chest: " + shortPos(target));
        }
    }

    public static void registerLookedAtChest(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            AutoBuildController.message("Look at a chest first", ChatFormatting.YELLOW);
            return;
        }
        BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        if (!isChest(minecraft, pos)) {
            AutoBuildController.message("The targeted block is not a chest", ChatFormatting.YELLOW);
            return;
        }
        AutoBuilderConfig.addMaterialChest(pos);
        AutoBuildController.message("Registered material chest: " + shortPos(pos), ChatFormatting.GREEN);
    }

    private static void takeMaterials(Minecraft minecraft, LocalPlayer player, ChestMenu menu) {
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        if (inventoryAlmostFull(player)) {
            player.closeContainer();
            boolean took = totalTakenStacks > 0;
            finish(
                    "Fetched " + totalTakenStacks + " stack(s); inventory is almost full",
                    took ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                    took
            );
            return;
        }
        int chestSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < chestSlots; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && isBuildMaterial(stack)) {
                minecraft.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
                clickCooldown = 3;
                currentChestTaken++;
                totalTakenStacks++;
                status = "Taking build materials: " + totalTakenStacks + " stack(s)";
                return;
            }
        }
        player.closeContainer();
        advanceAfterChest();
    }

    private static void advanceAfterChest() {
        if (targetIndex + 1 < targets.size()) {
            targetIndex++;
            selectTarget();
            state = State.WALKING;
            status = currentChestTaken == 0
                    ? "Chest empty, moving to next material chest"
                    : "Moving to next material chest";
            return;
        }
        if (totalTakenStacks > 0) {
            finish("Fetched " + totalTakenStacks + " material stack(s)", ChatFormatting.GREEN, true);
        } else {
            finish(materialShortageMessage(), ChatFormatting.RED, false);
        }
    }

    private static void skipCurrentChest(String reason) {
        AutoBuildController.message(reason, ChatFormatting.YELLOW);
        currentChestTaken = 0;
        advanceAfterChest();
    }

    private static void selectTarget() {
        target = targets.get(targetIndex);
        openAttempts = 0;
        clickCooldown = 0;
        pathCooldown = 0;
        currentChestTaken = 0;
    }

    private static void finish(String reason, ChatFormatting color) {
        finish(reason, color, totalTakenStacks > 0);
    }

    private static void finish(String reason, ChatFormatting color, boolean tookMaterials) {
        FetchCallback done = callback;
        boolean full = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            full = inventoryAlmostFull(minecraft.player);
        }
        int taken = totalTakenStacks;
        state = State.IDLE;
        targets = List.of();
        target = null;
        callback = null;
        targetIndex = 0;
        openAttempts = 0;
        clickCooldown = 0;
        pathCooldown = 0;
        currentChestTaken = 0;
        totalTakenStacks = 0;
        neededItems = Set.of();
        status = reason;
        AutoBuildController.message(reason, color);
        if (done != null) {
            done.finished(new FetchResult(tookMaterials, taken, full, reason));
        }
    }

    private static boolean isBuildMaterial(ItemStack stack) {
        if (!neededItems.isEmpty()) {
            return neededItems.contains(stack.getItem());
        }
        if (stack.getItem() instanceof BlockItem) {
            return true;
        }
        return stack.is(Items.STICK)
                || stack.is(Items.IRON_INGOT)
                || stack.is(Items.DIAMOND)
                || stack.is(Items.COBBLESTONE)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.STRING);
    }

    private static String materialShortageMessage() {
        return neededItems.isEmpty()
                ? "資材が足りません"
                : "資材が足りません: 登録チェストに設計図で必要な素材がありません";
    }

    private static boolean inventoryAlmostFull(LocalPlayer player) {
        int empty = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                empty++;
            }
        }
        return empty <= 2;
    }

    private static List<BlockPos> sortedMaterialChests() {
        Minecraft minecraft = Minecraft.getInstance();
        List<BlockPos> chests = new ArrayList<>(AutoBuilderConfig.materialChests());
        if (minecraft.player == null) {
            return chests;
        }
        chests.sort(Comparator.comparingDouble(pos -> minecraft.player.position().distanceToSqr(Vec3.atCenterOf(pos))));
        return chests;
    }

    private static boolean isChest(Minecraft minecraft, BlockPos pos) {
        if (minecraft == null || minecraft.level == null || pos == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof TrappedChestBlock;
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
