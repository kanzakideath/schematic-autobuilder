package com.kanzakideath.schematicautobuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BaritoneBridge {

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
            return "Open a Litematica/Schematica placement before starting";
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

    public static boolean pathToChest(BlockPos pos) {
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

    private static void setBooleanSetting(Object settings, String fieldName, boolean value) {
        try {
            Field settingField = settings.getClass().getField(fieldName);
            Object setting = settingField.get(settings);
            Field valueField = setting.getClass().getField("value");
            valueField.set(setting, value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static boolean startLegacyPlacedSchematicBuild(Object builder) {
        try {
            Method litematica = builder.getClass().getMethod("buildOpenLitematic", int.class);
            litematica.invoke(builder, 0);
            return true;
        } catch (ReflectiveOperationException ignored) {
            try {
                Method schematica = builder.getClass().getMethod("buildOpenSchematic");
                schematica.invoke(builder);
                return true;
            } catch (ReflectiveOperationException ignoredAgain) {
                return false;
            }
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
