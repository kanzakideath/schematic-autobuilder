package com.kanzakideath.schematicautobuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class MaterialChestProcess {

    private enum State {
        IDLE,
        WALKING,
        OPENING,
        TAKING
    }

    private static State state = State.IDLE;
    private static BlockPos target;
    private static int openAttempts;
    private static int clickCooldown;
    private static int takenStacks;
    private static String status = "Idle";

    private MaterialChestProcess() {}

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static String status() {
        return status;
    }

    public static void start() {
        if (state != State.IDLE) {
            stop("Material fetch cancelled");
            return;
        }
        List<BlockPos> chests = AutoBuilderConfig.materialChests();
        if (chests.isEmpty()) {
            status = "No material chest registered";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        target = nearestChest(chests);
        if (target == null) {
            status = "No reachable material chest target";
            message(status, ChatFormatting.YELLOW);
            return;
        }
        openAttempts = 0;
        clickCooldown = 0;
        takenStacks = 0;
        state = State.WALKING;
        status = "Going to material chest " + shortPos(target);
        message("素材チェストへ移動します: " + shortPos(target), ChatFormatting.AQUA);
    }

    public static void stop(String reason) {
        state = State.IDLE;
        target = null;
        openAttempts = 0;
        clickCooldown = 0;
        status = reason == null ? "Idle" : reason;
    }

    public static void tick(Minecraft minecraft) {
        if (state == State.IDLE || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (target == null) {
            stop("No target chest");
            return;
        }
        if (player.containerMenu instanceof ChestMenu chestMenu) {
            state = State.TAKING;
            takeMaterials(minecraft, player, chestMenu);
            return;
        }
        if (!isChest(minecraft, target)) {
            stop("Registered material chest disappeared");
            message("登録素材チェストが見つかりません", ChatFormatting.RED);
            return;
        }
        double distance = player.position().distanceTo(Vec3.atCenterOf(target));
        if (distance > 4.5D) {
            state = State.WALKING;
            status = "Walking to material chest " + shortPos(target);
            BaritoneBridge.pathToChest(target);
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
            stop("Could not open material chest");
            message("素材チェストを開けませんでした", ChatFormatting.RED);
        }
    }

    public static void registerLookedAtChest(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.hitResult == null || minecraft.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            message("チェストを見てから登録してください", ChatFormatting.YELLOW);
            return;
        }
        BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        if (!isChest(minecraft, pos)) {
            message("見ているブロックはチェストではありません", ChatFormatting.YELLOW);
            return;
        }
        AutoBuilderConfig.addMaterialChest(pos);
        message("素材チェストを登録しました: " + shortPos(pos), ChatFormatting.GREEN);
    }

    private static void takeMaterials(Minecraft minecraft, LocalPlayer player, ChestMenu menu) {
        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }
        if (inventoryAlmostFull(player)) {
            finishTaking(player);
            return;
        }
        int chestSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < chestSlots; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && isBuildMaterial(stack)) {
                minecraft.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
                clickCooldown = 3;
                takenStacks++;
                status = "Taking build materials: " + takenStacks + " stack(s)";
                return;
            }
        }
        finishTaking(player);
    }

    private static void finishTaking(LocalPlayer player) {
        player.closeContainer();
        String line = takenStacks == 0 ? "No build materials found in chest" : "Fetched " + takenStacks + " material stack(s)";
        message(line, takenStacks == 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        stop(line);
        if (AutoBuilderConfig.startBuildAfterFetch()) {
            BaritoneBridge.startPlacedSchematicBuild();
        }
    }

    private static boolean isBuildMaterial(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) {
            return true;
        }
        return stack.is(Items.STICK) || stack.is(Items.IRON_INGOT) || stack.is(Items.DIAMOND) || stack.is(Items.COBBLESTONE);
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

    private static BlockPos nearestChest(List<BlockPos> chests) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return chests.isEmpty() ? null : chests.get(0);
        }
        return chests.stream()
                .min(Comparator.comparingDouble(pos -> minecraft.player.position().distanceToSqr(Vec3.atCenterOf(pos))))
                .orElse(null);
    }

    private static boolean isChest(Minecraft minecraft, BlockPos pos) {
        if (minecraft == null || minecraft.level == null || pos == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof TrappedChestBlock;
    }

    private static void message(String text, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gui != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[AutoBuilder] " + text).withStyle(color));
        }
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
