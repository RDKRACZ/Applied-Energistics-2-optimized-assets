/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.pattern;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import appeng.api.storage.StorageChannels;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.execution.GenericStackHelper;
import appeng.util.item.AEItemStack;

/**
 * Helper functions to work with patterns, mostly related to (de)serialization.
 */
class AEPatternHelper {
    public static final String NBT_INPUTS = "in";
    public static final String NBT_RESULT = "result";
    public static final String NBT_OUTPUTS = "out";
    public static final String NBT_SUBSITUTE = "substitute";
    public static final String NBT_RECIPE_ID = "recipe";
    private static final Comparator<IAEStack> COMPARE_BY_STACKSIZE = (left, right) -> Long
            .compare(right.getStackSize(), left.getStackSize());

    public static IAEStack[] getProcessingInputs(CompoundTag nbt) {
        return getMixedList(nbt, NBT_INPUTS, 9);
    }

    public static IAEStack[] getProcessingOutputs(CompoundTag nbt) {
        return getMixedList(nbt, NBT_OUTPUTS, 3);
    }

    public static IAEStack[] getMixedList(CompoundTag nbt, String nbtKey, int maxSize) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        ListTag tag = nbt.getList(nbtKey, Tag.TAG_COMPOUND);
        Preconditions.checkArgument(tag.size() <= maxSize, "Cannot use more than " + maxSize + " ingredients");

        var result = new IAEStack[tag.size()];
        for (int x = 0; x < tag.size(); ++x) {
            var stack = GenericStackHelper.readGenericStack(tag.getCompound(x));
            if (stack != null) {
                if (stack.getChannel() != StorageChannels.items() && stack.getChannel() != StorageChannels.fluids()) {
                    throw new IllegalArgumentException("Only items and fluids are supported in AE2 patterns.");
                }
            }
            result[x] = stack;
        }
        return result;
    }

    public static IAEItemStack[] getCraftingInputs(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        ListTag tag = nbt.getList(NBT_INPUTS, Tag.TAG_COMPOUND);
        Preconditions.checkArgument(tag.size() <= 9, "Cannot use more than 9 ingredients");

        var result = new IAEItemStack[tag.size()];
        for (int x = 0; x < tag.size(); ++x) {
            var stack = AEItemStack.fromItemStack(ItemStack.of(tag.getCompound(x)));
            result[x] = stack;
        }
        return result;

    }

    public static <T extends IAEStack> T[] condenseStacks(T[] collection) {
        final List<T> merged = Arrays.stream(collection).filter(Objects::nonNull)
                .collect(Collectors.toMap(Function.identity(), IAEStack::copy,
                        (left, right) -> {
                            left.setStackSize(left.getStackSize() + right.getStackSize());
                            return left;
                        }))
                .values().stream().sorted(COMPARE_BY_STACKSIZE).collect(ImmutableList.toImmutableList());

        if (merged.isEmpty()) {
            throw new IllegalStateException("No pattern here!");
        }

        return merged.toArray(Arrays.copyOf(collection, 0));
    }

    public static boolean canSubstitute(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return nbt.getBoolean(NBT_SUBSITUTE);
    }

    public static ResourceLocation getRecipeId(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return new ResourceLocation(nbt.getString(NBT_RECIPE_ID));
    }

    public static ItemStack getCraftingResult(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return ItemStack.of(nbt.getCompound(NBT_OUTPUTS));
    }

    public static void encodeProcessingPattern(CompoundTag tag, IAEStack[] sparseInputs, IAEStack[] sparseOutputs) {
        tag.put(NBT_INPUTS, encodeStackList(sparseInputs));
        tag.put(NBT_OUTPUTS, encodeStackList(sparseOutputs));
    }

    private static ListTag encodeStackList(IAEStack[] stacks) {
        ListTag tag = new ListTag();
        boolean foundStack = false;
        for (var stack : stacks) {
            tag.add(GenericStackHelper.writeGenericStack(stack));
            if (stack != null && stack.getStackSize() > 0) {
                foundStack = true;
            }
        }
        Preconditions.checkArgument(foundStack, "List passed to pattern must contain at least one stack.");
        return tag;
    }

    public static void encodeCraftingPattern(CompoundTag tag, CraftingRecipe recipe, ItemStack[] sparseInputs,
            ItemStack output, boolean allowSubstitution) {
        tag.put(NBT_INPUTS, encodeItemStackList(sparseInputs));
        tag.putBoolean(NBT_SUBSITUTE, allowSubstitution);
        tag.put(NBT_OUTPUTS, output.save(new CompoundTag()));
        tag.putString(NBT_RECIPE_ID, recipe.getId().toString());
    }

    private static ListTag encodeItemStackList(ItemStack[] stacks) {
        ListTag tag = new ListTag();
        boolean foundStack = false;
        for (var stack : stacks) {
            tag.add(stack.serializeNBT());
            if (!stack.isEmpty()) {
                foundStack = true;
            }
        }
        Preconditions.checkArgument(foundStack, "List passed to pattern must contain at least one stack.");
        return tag;
    }
}
