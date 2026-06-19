package com.kanzakideath.schematicautobuilder;

import net.minecraft.core.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
            return Boolean.TRUE.equals(method.invoke(builder)) ? "Builder active" : "Builder idle";
        } catch (ReflectiveOperationException ignored) {
            return "Baritone ready";
        }
    }

    public static boolean startPlacedSchematicBuild() {
        Object builder = builderProcess();
        if (builder == null) {
            return false;
        }
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
}

