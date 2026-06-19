package com.kanzakideath.schematicautobuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BaritoneBridge {
    private static final List<String> WOOD_SUFFIXES = List.of(
            "planks",
            "stairs",
            "slab",
            "fence",
            "fence_gate",
            "door",
            "trapdoor",
            "button",
            "pressure_plate",
            "sign",
            "wall_sign",
            "hanging_sign",
            "wall_hanging_sign",
            "log",
            "wood",
            "stem",
            "hyphae"
    );
    private static final List<String> COLOR_PREFIXES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );
    private static final List<String> COLOR_SUFFIXES = List.of(
            "stained_glass",
            "stained_glass_pane",
            "wool",
            "carpet",
            "concrete",
            "concrete_powder",
            "terracotta",
            "glazed_terracotta"
    );
    private static final Set<String> WOOD_INGREDIENT_SUFFIXES = Set.of(
            "planks",
            "log",
            "wood",
            "stem",
            "hyphae",
            "stripped_log",
            "stripped_wood",
            "stripped_stem",
            "stripped_hyphae"
    );
    private static final List<Item> PREFERRED_SCAFFOLD_ITEMS = List.of(
            Blocks.DIRT.asItem(),
            Blocks.COBBLESTONE.asItem(),
            Blocks.NETHERRACK.asItem(),
            Blocks.STONE.asItem(),
            Blocks.COBBLED_DEEPSLATE.asItem(),
            Blocks.OAK_PLANKS.asItem(),
            Blocks.BIRCH_PLANKS.asItem(),
            Blocks.SPRUCE_PLANKS.asItem(),
            Blocks.JUNGLE_PLANKS.asItem(),
            Blocks.ACACIA_PLANKS.asItem(),
            Blocks.DARK_OAK_PLANKS.asItem(),
            Blocks.MANGROVE_PLANKS.asItem(),
            Blocks.CHERRY_PLANKS.asItem(),
            Blocks.BAMBOO_PLANKS.asItem()
    );

    public record BuildStats(
            int totalBlocks,
            int doneBlocks,
            int remainingBlocks,
            int failedBlocks,
            int unreachableBlocks,
            String target
    ) {
        public static BuildStats empty() {
            return new BuildStats(0, 0, 0, 0, 0, "");
        }

        public double progress() {
            if (totalBlocks <= 0) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(100.0D, doneBlocks * 100.0D / totalBlocks));
        }
    }

    private BaritoneBridge() {}

    public static boolean isAvailable() {
        return primaryBaritone() != null;
    }

    public static String status() {
        if (!isAvailable()) {
            return "Baritone not found";
        }
        Object builder = builderProcess();
        if (builder == null) {
            return "Builder process not found";
        }
        try {
            Method method = builder.getClass().getMethod("isActive");
            boolean active = Boolean.TRUE.equals(method.invoke(builder));
            if (active && isBuilderPaused()) {
                return "Builder paused";
            }
            return active ? "Builder active" : "Builder idle";
        } catch (ReflectiveOperationException ignored) {
            return "Baritone ready";
        }
    }

    public static String hudStatus() {
        if (!isAvailable()) {
            return "failed";
        }
        if (isBuilderPaused()) {
            return "paused";
        }
        if (isPathing()) {
            return isBuilderActive() ? "building/pathing" : "pathing";
        }
        if (isBuilderActive()) {
            return "building";
        }
        return "idle";
    }

    public static BuildStats buildStats() {
        Object builder = builderProcess();
        if (builder == null) {
            return BuildStats.empty();
        }
        try {
            Object schematic = privateField(builder, "schematic");
            Object incorrect = privateField(builder, "incorrectPositions");
            Object observed = privateField(builder, "observedCompleted");
            Object originObject = privateField(builder, "origin");
            Object approxObject = privateField(builder, "approxPlaceable");
            if (schematic == null || incorrect == null || !(originObject instanceof Vec3i origin)) {
                return BuildStats.empty();
            }
            int remaining = Math.max(0, sizeOf(incorrect));
            int done = Math.max(0, sizeOf(observed));
            int total = Math.max(done + remaining, remaining);
            String target = "";
            if (incorrect instanceof Iterable<?> iterable) {
                List<?> approx = approxObject instanceof List<?> list ? list : Collections.emptyList();
                target = firstTargetDescription(schematic, iterable, origin, approx);
            }
            return new BuildStats(total, done, remaining, 0, 0, target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return BuildStats.empty();
        }
    }

    public static boolean startPlacedSchematicBuild() {
        Object builder = builderProcess();
        if (builder == null) {
            return false;
        }
        configureForExactSchematicBuild();
        try {
            Method modern = builder.getClass().getMethod("buildFirstOpenSchematic");
            Object result = modern.invoke(builder);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (NoSuchMethodException ignored) {
            return startLegacyPlacedSchematicBuild(builder);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static String openSchematicStatus() {
        Object builder = builderProcess();
        if (builder == null) {
            return "Baritone builder process not found";
        }
        try {
            Method method = builder.getClass().getMethod("openSchematicStatus");
            Object result = method.invoke(builder);
            return result == null ? "No schematic status" : result.toString();
        } catch (ReflectiveOperationException ignored) {
            return legacyOpenSchematicStatus();
        }
    }

    public static boolean isBuilderActive() {
        Object builder = builderProcess();
        if (builder == null) {
            return false;
        }
        try {
            Method method = builder.getClass().getMethod("isActive");
            return Boolean.TRUE.equals(method.invoke(builder));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean isBuilderPaused() {
        Object builder = builderProcess();
        if (builder == null) {
            return false;
        }
        try {
            Method method = builder.getClass().getMethod("isPaused");
            return Boolean.TRUE.equals(method.invoke(builder));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void pauseBuilder() {
        invokeBuilderNoArgs("pause");
    }

    public static void resumeBuilder() {
        invokeBuilderNoArgs("resume");
    }

    public static boolean isClearingAreaActive() {
        Object builder = builderProcess();
        if (builder == null) {
            return false;
        }
        try {
            Method method = builder.getClass().getMethod("isClearingArea");
            return Boolean.TRUE.equals(method.invoke(builder));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean toggleClearAreaPause() {
        try {
            Class<?> control = Class.forName("baritone.utils.ClearAreaPauseControl");
            Object result = control.getMethod("togglePrimary").invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean matchesClearAreaPauseKey(KeyEvent event) {
        if (event == null) {
            return false;
        }
        try {
            Class<?> bindings = Class.forName("baritone.utils.BaritoneKeyBindings");
            Object result = bindings.getMethod("matchesClearAreaPause", KeyEvent.class).invoke(null, event);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean cancelPathing() {
        Object baritone = primaryBaritone();
        if (baritone == null) {
            return false;
        }
        try {
            Object pathing = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            Object result = pathing.getClass().getMethod("cancelEverything").invoke(pathing);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isPathing() {
        Object baritone = primaryBaritone();
        if (baritone == null) {
            return false;
        }
        try {
            Object pathing = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            Object result = pathing.getClass().getMethod("isPathing").invoke(pathing);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean pathToChest(BlockPos pos) {
        return pathToBlock(pos);
    }

    public static boolean pathToBlock(BlockPos pos) {
        Object baritone = primaryBaritone();
        if (baritone == null || pos == null) {
            return false;
        }
        try {
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Object goal = createGoalGetToBlock(pos);
            if (goal == null) {
                return false;
            }
            customGoal.getClass().getMethod("setGoalAndPath", goalInterface()).invoke(customGoal, goal);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static List<BlockPos> clearAreaStorageChests() {
        Object settings = settings();
        if (settings == null) {
            return List.of();
        }
        List<BlockPos> result = new ArrayList<>();
        addSettingsChestPositions(settings, "clearAreaStorageChests", result);
        return result;
    }

    public static void configureForExactSchematicBuild() {
        Object settings = settings();
        if (settings == null) {
            return;
        }
        setBooleanSetting(settings, "allowBreak", true);
        setBooleanSetting(settings, "allowPlace", true);
        setBooleanSetting(settings, "allowInventory", true);
        setBooleanSetting(settings, "buildIgnoreExisting", false);
        setBooleanSetting(settings, "buildIgnoreDirection", false);
        setBooleanSetting(settings, "buildOnlySelection", false);
        setBooleanSetting(settings, "buildInLayers", true);
        setBooleanSetting(settings, "layerOrder", AutoBuilderConfig.topDownBuild());
        setBooleanSetting(settings, "skipFailedLayers", false);
        setBooleanSetting(settings, "okIfWater", false);
        setBooleanSetting(settings, "mapArtMode", false);
        setIntegerSetting(settings, "startAtLayer", 0);
        clearCollectionSetting(settings, "buildIgnoreBlocks");
        clearCollectionSetting(settings, "buildSkipBlocks");
        clearCollectionSetting(settings, "okIfAir");
        clearMapSetting(settings, "buildValidSubstitutes");
        clearMapSetting(settings, "buildSubstitutes");
        if (AutoBuilderConfig.autoSubstituteMaterials()) {
            Map<Block, List<Block>> substitutes = automaticMaterialSubstitutes();
            putMapSettingValues(settings, "buildSubstitutes", substitutes);
            putMapSettingValues(settings, "buildValidSubstitutes", substitutes);
        }
    }

    public static void configureTemporaryScaffoldItems(Set<Item> neededItems) {
        Object settings = settings();
        Minecraft minecraft = Minecraft.getInstance();
        if (settings == null || minecraft.player == null) {
            return;
        }
        Set<Item> needed = neededItems == null ? Set.of() : neededItems;
        List<Item> candidates = new ArrayList<>();
        for (Item item : PREFERRED_SCAFFOLD_ITEMS) {
            if (item != Items.AIR && !needed.contains(item) && !candidates.contains(item)) {
                candidates.add(item);
            }
        }
        for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem) || needed.contains(stack.getItem())) {
                continue;
            }
            if (!candidates.contains(stack.getItem())) {
                candidates.add(stack.getItem());
            }
            if (candidates.size() >= 24) {
                break;
            }
        }
        clearCollectionSetting(settings, "acceptableThrowawayItems");
        addCollectionSettingValues(settings, "acceptableThrowawayItems", candidates);
    }

    public static List<Item> preferredScaffoldItems(Set<Item> neededItems) {
        Set<Item> needed = neededItems == null ? Set.of() : neededItems;
        List<Item> result = new ArrayList<>();
        for (Item item : PREFERRED_SCAFFOLD_ITEMS) {
            if (item != Items.AIR && !needed.contains(item) && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static Set<Item> currentNeededBuildItems() {
        Object builder = builderProcess();
        if (builder == null) {
            return Set.of();
        }
        try {
            Object schematic = privateField(builder, "schematic");
            Object incorrect = privateField(builder, "incorrectPositions");
            Object originObject = privateField(builder, "origin");
            Object approxObject = privateField(builder, "approxPlaceable");
            if (schematic == null || !(incorrect instanceof Iterable<?>) || !(originObject instanceof Vec3i origin)) {
                return Set.of();
            }
            List<?> approx = approxObject instanceof List<?> list ? list : Collections.emptyList();
            Method desiredState = schematic.getClass().getMethod(
                    "desiredState",
                    int.class,
                    int.class,
                    int.class,
                    BlockState.class,
                    List.class
            );
            desiredState.setAccessible(true);
            Set<Item> result = new HashSet<>();
            int scanned = 0;
            for (Object posObject : (Iterable<?>) incorrect) {
                if (!(posObject instanceof BlockPos pos)) {
                    continue;
                }
                BlockState current = MinecraftHolder.currentBlockState(pos);
                Object desiredObject = desiredState.invoke(
                        schematic,
                        pos.getX() - origin.getX(),
                        pos.getY() - origin.getY(),
                        pos.getZ() - origin.getZ(),
                        current,
                        approx
                );
                if (desiredObject instanceof BlockState desired && !desired.isAir()) {
                    Item item = desired.getBlock().asItem();
                    if (item != Items.AIR) {
                        result.add(item);
                    }
                }
                scanned++;
                if (scanned >= 512) {
                    break;
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Set.of();
        }
    }

    public static boolean isAutoSubstituteMaterial(Item item, Set<Item> neededItems) {
        if (!AutoBuilderConfig.autoSubstituteMaterials() || item == null || neededItems == null || neededItems.isEmpty() || !(item instanceof BlockItem blockItem)) {
            return false;
        }
        Block candidate = blockItem.getBlock();
        String candidateGroup = substituteGroupKey(candidate);
        String candidateWoodSuffix = woodSuffix(blockPath(candidate));
        for (Item neededItem : neededItems) {
            if (item == neededItem) {
                return true;
            }
            if (!(neededItem instanceof BlockItem neededBlockItem)) {
                continue;
            }
            Block needed = neededBlockItem.getBlock();
            String neededGroup = substituteGroupKey(needed);
            if (candidateGroup != null && candidateGroup.equals(neededGroup)) {
                return true;
            }
            if (neededGroup != null && neededGroup.startsWith("wood:") && WOOD_INGREDIENT_SUFFIXES.contains(candidateWoodSuffix)) {
                return true;
            }
        }
        return false;
    }

    private static Map<Block, List<Block>> automaticMaterialSubstitutes() {
        Map<String, List<Block>> groups = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.stream().forEach(block -> {
            String group = substituteGroupKey(block);
            if (group != null) {
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(block);
            }
        });
        Map<Block, Integer> inventoryCounts = inventoryBlockCounts();
        Map<String, Integer> woodFamilyCounts = inventoryWoodFamilyCounts();
        boolean creative = isCreativePlayer();
        Map<Block, List<Block>> result = new HashMap<>();
        for (Map.Entry<String, List<Block>> entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<Block> ordered = orderedSubstitutes(entry.getKey(), entry.getValue(), inventoryCounts, woodFamilyCounts, creative);
            for (Block block : entry.getValue()) {
                result.put(block, ordered);
            }
        }
        return result;
    }

    private static List<Block> orderedSubstitutes(String group, List<Block> blocks, Map<Block, Integer> inventoryCounts, Map<String, Integer> woodFamilyCounts, boolean creative) {
        return blocks.stream()
                .sorted(Comparator
                        .comparingInt((Block block) -> substituteScore(group, block, inventoryCounts, woodFamilyCounts, creative))
                        .reversed()
                        .thenComparing(BaritoneBridge::blockPath))
                .toList();
    }

    private static int substituteScore(String group, Block block, Map<Block, Integer> inventoryCounts, Map<String, Integer> woodFamilyCounts, boolean creative) {
        String path = blockPath(block);
        int score = 0;
        if (!creative) {
            score += inventoryCounts.getOrDefault(block, 0) > 0 ? 10_000 : 0;
            if (group.startsWith("wood:")) {
                score += woodFamilyCounts.getOrDefault(woodFamily(path), 0) > 0 ? 1_000 : 0;
            }
        }
        score += fallbackPriority(group, path);
        return score;
    }

    private static int fallbackPriority(String group, String path) {
        if ("color:stained_glass".equals(group)) {
            return "glass".equals(path) ? 500 : colorFallbackPriority(path, "stained_glass");
        }
        if ("color:stained_glass_pane".equals(group)) {
            return "glass_pane".equals(path) ? 500 : colorFallbackPriority(path, "stained_glass_pane");
        }
        if ("color:terracotta".equals(group)) {
            return "terracotta".equals(path) ? 500 : colorFallbackPriority(path, "terracotta");
        }
        if (group.startsWith("color:")) {
            return colorFallbackPriority(path, group.substring("color:".length()));
        }
        if (group.startsWith("wood:")) {
            return woodFallbackPriority(woodFamily(path));
        }
        return 0;
    }

    private static int colorFallbackPriority(String path, String suffix) {
        for (int i = 0; i < COLOR_PREFIXES.size(); i++) {
            if (path.equals(COLOR_PREFIXES.get(i) + "_" + suffix)) {
                return 200 - i;
            }
        }
        return 0;
    }

    private static int woodFallbackPriority(String family) {
        if (family == null) {
            return 0;
        }
        return switch (family) {
            case "oak" -> 300;
            case "birch" -> 290;
            case "spruce" -> 280;
            case "jungle" -> 270;
            case "acacia" -> 260;
            case "dark_oak" -> 250;
            case "mangrove" -> 240;
            case "cherry" -> 230;
            case "bamboo" -> 220;
            case "crimson" -> 210;
            case "warped" -> 200;
            default -> 100;
        };
    }

    private static boolean isCreativePlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isCreative();
    }

    private static Map<Block, Integer> inventoryBlockCounts() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Map.of();
        }
        Map<Block, Integer> result = new HashMap<>();
        for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                result.merge(blockItem.getBlock(), stack.getCount(), Integer::sum);
            }
        }
        return result;
    }

    private static Map<String, Integer> inventoryWoodFamilyCounts() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>();
        for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            String family = woodFamily(blockPath(blockItem.getBlock()));
            if (family != null) {
                result.merge(family, stack.getCount(), Integer::sum);
            }
        }
        return result;
    }

    private static String substituteGroupKey(Block block) {
        return substituteGroupKey(blockPath(block));
    }

    private static String substituteGroupKey(String path) {
        String woodSuffix = woodSuffix(path);
        if (woodSuffix != null) {
            return "wood:" + woodSuffix;
        }
        String colorGroup = colorGroupKey(path);
        if (colorGroup != null) {
            return colorGroup;
        }
        return null;
    }

    private static String woodSuffix(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> suffixes = WOOD_SUFFIXES.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String suffix : suffixes) {
            if (path.startsWith("stripped_") && isStrippableSuffix(suffix) && path.endsWith("_" + suffix)) {
                return "stripped_" + suffix;
            }
            if (path.endsWith("_" + suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static boolean isStrippableSuffix(String suffix) {
        return "log".equals(suffix) || "wood".equals(suffix) || "stem".equals(suffix) || "hyphae".equals(suffix);
    }

    private static String woodFamily(String path) {
        String suffix = woodSuffix(path);
        if (suffix == null) {
            return null;
        }
        String basePath = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
        String baseSuffix = suffix.startsWith("stripped_") ? suffix.substring("stripped_".length()) : suffix;
        String end = "_" + baseSuffix;
        if (!basePath.endsWith(end)) {
            return null;
        }
        return basePath.substring(0, basePath.length() - end.length());
    }

    private static String colorGroupKey(String path) {
        if ("glass".equals(path)) {
            return "color:stained_glass";
        }
        if ("glass_pane".equals(path)) {
            return "color:stained_glass_pane";
        }
        if ("terracotta".equals(path)) {
            return "color:terracotta";
        }
        for (String color : COLOR_PREFIXES) {
            for (String suffix : COLOR_SUFFIXES) {
                if (path.equals(color + "_" + suffix)) {
                    return "color:" + suffix;
                }
            }
        }
        return null;
    }

    private static String blockPath(Block block) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "" : key.getPath();
    }

    private static void invokeBuilderNoArgs(String methodName) {
        Object builder = builderProcess();
        if (builder == null) {
            return;
        }
        try {
            builder.getClass().getMethod(methodName).invoke(builder);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object settings() {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            return api.getMethod("getSettings").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object privateField(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static int sizeOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        try {
            Object result = value.getClass().getMethod("size").invoke(value);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static String firstTargetDescription(Object schematic, Iterable<?> incorrect, Vec3i origin, List<?> approx) {
        try {
            Method desiredState = schematic.getClass().getMethod(
                    "desiredState",
                    int.class,
                    int.class,
                    int.class,
                    BlockState.class,
                    List.class
            );
            desiredState.setAccessible(true);
            int scanned = 0;
            for (Object posObject : incorrect) {
                if (!(posObject instanceof BlockPos pos)) {
                    continue;
                }
                BlockState current = MinecraftHolder.currentBlockState(pos);
                Object desiredObject = desiredState.invoke(
                        schematic,
                        pos.getX() - origin.getX(),
                        pos.getY() - origin.getY(),
                        pos.getZ() - origin.getZ(),
                        current,
                        approx
                );
                if (desiredObject instanceof BlockState desired && !desired.isAir()) {
                    return blockPath(desired.getBlock()) + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
                }
                scanned++;
                if (scanned >= 128) {
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return "";
    }

    private static void setBooleanSetting(Object settings, String fieldName, boolean value) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            valueField.set(setting, value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void setIntegerSetting(Object settings, String fieldName, int value) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            valueField.set(setting, value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void clearCollectionSetting(Object settings, String fieldName) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            Object value = valueField.get(setting);
            if (value instanceof Collection<?> collection) {
                collection.clear();
            }
        } catch (ReflectiveOperationException | LinkageError | UnsupportedOperationException ignored) {
        }
    }

    private static void clearMapSetting(Object settings, String fieldName) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            Object value = valueField.get(setting);
            if (value instanceof Map<?, ?> map) {
                map.clear();
            }
        } catch (ReflectiveOperationException | LinkageError | UnsupportedOperationException ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putMapSettingValues(Object settings, String fieldName, Map<?, ?> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            Object value = valueField.get(setting);
            if (value instanceof Map map) {
                map.putAll(additions);
            }
        } catch (ReflectiveOperationException | LinkageError | UnsupportedOperationException ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addCollectionSettingValues(Object settings, String fieldName, Collection<?> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            Object value = valueField.get(setting);
            if (value instanceof Collection collection) {
                for (Object addition : additions) {
                    if (!collection.contains(addition)) {
                        collection.add(addition);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | UnsupportedOperationException ignored) {
        }
    }

    private static void addSettingsChestPositions(Object settings, String fieldName, List<BlockPos> result) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            Object value = valueField.get(setting);
            if (!(value instanceof Iterable<?> encodedPositions)) {
                return;
            }
            for (Object encoded : encodedPositions) {
                BlockPos pos = decodeSettingsPos(encoded == null ? "" : encoded.toString());
                if (pos != null && !result.contains(pos)) {
                    result.add(pos);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static BlockPos decodeSettingsPos(String value) {
        String[] parts = value.trim().split(";");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean startLegacyPlacedSchematicBuild(Object builder) {
        try {
            if (hasLegacyLitematicaPlacement()) {
                Method litematica = builder.getClass().getMethod("buildOpenLitematic", int.class);
                litematica.invoke(builder, 0);
                return isBuilderActive();
            }
            if (hasLegacySchematicaPlacement()) {
                Method schematica = builder.getClass().getMethod("buildOpenSchematic");
                schematica.invoke(builder);
                return isBuilderActive();
            }
            return false;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String legacyOpenSchematicStatus() {
        if (hasLegacyLitematicaPlacement()) {
            return "Litematica placement ready";
        }
        if (hasLegacySchematicaPlacement()) {
            return "Schematica placement ready";
        }
        return "No placed Litematica/Schematica schematic found";
    }

    private static boolean hasLegacyLitematicaPlacement() {
        try {
            Class<?> helper = Class.forName("baritone.utils.schematic.litematica.LitematicaHelper");
            Method present = helper.getMethod("isLitematicaPresent");
            if (!Boolean.TRUE.equals(present.invoke(null))) {
                return false;
            }
            try {
                Method loadedCount = helper.getMethod("loadedSchematicCount");
                Object count = loadedCount.invoke(null);
                return count instanceof Integer && (Integer) count > 0;
            } catch (NoSuchMethodException ignored) {
                Method hasLoaded = helper.getMethod("hasLoadedSchematic", int.class);
                return Boolean.TRUE.equals(hasLoaded.invoke(null, 0));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasLegacySchematicaPlacement() {
        try {
            Class<?> helper = Class.forName("baritone.utils.schematic.schematica.SchematicaHelper");
            Method present = helper.getMethod("isSchematicaPresent");
            if (!Boolean.TRUE.equals(present.invoke(null))) {
                return false;
            }
            Method open = helper.getMethod("getOpenSchematic");
            Object optional = open.invoke(null);
            return optional instanceof java.util.Optional<?> && ((java.util.Optional<?>) optional).isPresent();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object builderProcess() {
        Object baritone = primaryBaritone();
        if (baritone == null) {
            return null;
        }
        try {
            return baritone.getClass().getMethod("getBuilderProcess").invoke(baritone);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object primaryBaritone() {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object createGoalGetToBlock(BlockPos pos) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName("baritone.api.pathing.goals.GoalGetToBlock");
        Constructor<?> constructor = goalClass.getConstructor(BlockPos.class);
        return constructor.newInstance(pos);
    }

    private static Class<?> goalInterface() throws ClassNotFoundException {
        return Class.forName("baritone.api.pathing.goals.Goal");
    }

    private static final class MinecraftHolder {
        private static BlockState currentBlockState(BlockPos pos) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.level == null) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            return minecraft.level.getBlockState(pos);
        }
    }
}
