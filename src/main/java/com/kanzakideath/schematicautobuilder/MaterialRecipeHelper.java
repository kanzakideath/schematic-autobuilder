package com.kanzakideath.schematicautobuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MaterialRecipeHelper {

    private MaterialRecipeHelper() {}

    public static boolean isUsefulForNeeded(ItemStack stack, Set<Item> neededItems) {
        if (stack.isEmpty() || neededItems.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (craftingIngredientsForNeeded(neededItems).contains(item)) {
            return true;
        }
        if (smeltingIngredientsForNeeded(neededItems).contains(item)) {
            return true;
        }
        return isLikelyFuel(stack) && neededNeedsSmelting(neededItems);
    }

    public static RecipeDisplayEntry findCraftableCraftingRecipe(Minecraft minecraft, LocalPlayer player, Set<Item> neededItems) {
        if (minecraft == null || minecraft.level == null || player == null || neededItems.isEmpty()) {
            return null;
        }
        StackedItemContents contents = new StackedItemContents();
        player.getInventory().fillStackedContents(contents);
        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        for (RecipeDisplayEntry entry : recipeEntries(player)) {
            RecipeDisplay display = entry.display();
            if (!(display instanceof ShapedCraftingRecipeDisplay) && !(display instanceof ShapelessCraftingRecipeDisplay)) {
                continue;
            }
            if (isNeededResult(entry, context, neededItems) && entry.canCraft(contents)) {
                return entry;
            }
        }
        return null;
    }

    private static Set<Item> craftingIngredientsForNeeded(Set<Item> neededItems) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return Set.of();
        }
        Set<Item> result = new HashSet<>();
        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        for (RecipeDisplayEntry entry : recipeEntries(minecraft.player)) {
            RecipeDisplay display = entry.display();
            if (!(display instanceof ShapedCraftingRecipeDisplay) && !(display instanceof ShapelessCraftingRecipeDisplay)) {
                continue;
            }
            if (!isNeededResult(entry, context, neededItems)) {
                continue;
            }
            addCraftingRequirementItems(entry, result);
            if (display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
                addSlotDisplayItems(shapedDisplay.ingredients(), context, result);
            } else if (display instanceof ShapelessCraftingRecipeDisplay shapelessDisplay) {
                addSlotDisplayItems(shapelessDisplay.ingredients(), context, result);
            }
        }
        addCommonFallbackIngredients(neededItems, result);
        return result;
    }

    private static Set<Item> smeltingIngredientsForNeeded(Set<Item> neededItems) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return Set.of();
        }
        Set<Item> result = new HashSet<>();
        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        for (RecipeDisplayEntry entry : recipeEntries(minecraft.player)) {
            RecipeDisplay display = entry.display();
            if (!(display instanceof FurnaceRecipeDisplay furnaceDisplay) || !isNeededResult(entry, context, neededItems)) {
                continue;
            }
            addSlotDisplayItems(List.of(furnaceDisplay.ingredient()), context, result);
        }
        addCommonFallbackSmeltingSources(neededItems, result);
        return result;
    }

    private static boolean neededNeedsSmelting(Set<Item> neededItems) {
        return !smeltingIngredientsForNeeded(neededItems).isEmpty();
    }

    private static boolean isNeededResult(RecipeDisplayEntry entry, ContextMap context, Set<Item> neededItems) {
        for (ItemStack stack : entry.resultItems(context)) {
            if (!stack.isEmpty() && neededItems.contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static void addCraftingRequirementItems(RecipeDisplayEntry entry, Set<Item> result) {
        Optional<List<Ingredient>> requirements = entry.craftingRequirements();
        if (requirements.isEmpty()) {
            return;
        }
        for (Ingredient ingredient : requirements.get()) {
            ingredient.items().forEach(holder -> result.add(holder.value()));
        }
    }

    private static void addSlotDisplayItems(List<SlotDisplay> displays, ContextMap context, Set<Item> result) {
        for (SlotDisplay display : displays) {
            for (ItemStack stack : display.resolveForStacks(context)) {
                if (!stack.isEmpty()) {
                    result.add(stack.getItem());
                }
            }
        }
    }

    private static List<RecipeDisplayEntry> recipeEntries(LocalPlayer player) {
        List<RecipeDisplayEntry> result = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (seen.add(entry.id())) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private static boolean isLikelyFuel(ItemStack stack) {
        return stack.is(Items.COAL)
                || stack.is(Items.CHARCOAL)
                || stack.is(Items.COAL_BLOCK)
                || stack.is(Items.LAVA_BUCKET)
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.PLANKS);
    }

    private static void addCommonFallbackIngredients(Set<Item> neededItems, Set<Item> result) {
        if (neededItems.contains(Items.STONE_BRICK_SLAB)) {
            result.add(Items.STONE_BRICKS);
            result.add(Items.STONE);
            result.add(Items.COBBLESTONE);
        }
        if (neededItems.contains(Items.STONE_BRICKS)) {
            result.add(Items.STONE);
            result.add(Items.COBBLESTONE);
        }
        if (neededItems.contains(Items.SMOOTH_STONE_SLAB)) {
            result.add(Items.SMOOTH_STONE);
            result.add(Items.STONE);
            result.add(Items.COBBLESTONE);
        }
        if (neededItems.contains(Items.GLASS)) {
            result.add(Items.SAND);
        }
    }

    private static void addCommonFallbackSmeltingSources(Set<Item> neededItems, Set<Item> result) {
        if (neededItems.contains(Items.STONE) || neededItems.contains(Items.STONE_BRICKS) || neededItems.contains(Items.STONE_BRICK_SLAB) || neededItems.contains(Items.SMOOTH_STONE_SLAB)) {
            result.add(Items.COBBLESTONE);
        }
        if (neededItems.contains(Items.SMOOTH_STONE) || neededItems.contains(Items.SMOOTH_STONE_SLAB)) {
            result.add(Items.STONE);
        }
        if (neededItems.contains(Items.GLASS)) {
            result.add(Items.SAND);
        }
    }
}
