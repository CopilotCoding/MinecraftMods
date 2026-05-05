package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

public class PetriDishItem extends Item {

    private static final String NBT_STATE      = "DishState";
    private static final String NBT_STRAINS    = "StrainIds";
    private static final String NBT_START_TICK = "StartTick";
    private static final String NBT_DURATION   = "Duration";
    private static final String NBT_MUTATED    = "Mutated";

    public enum DishState { EMPTY, CULTURING, READY }

    public PetriDishItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack dishStack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        DishState state = getState(dishStack);

        // ── Culture transfer: ready dish (main) + empty dish (off-hand) + agar in inventory ──
        if (state == DishState.READY
                && otherStack.getItem() instanceof PetriDishItem
                && getState(otherStack) == DishState.EMPTY) {

            if (!level.isClientSide) {
                // Find nutrient agar in inventory
                boolean hasAgar = false;
                if (player instanceof net.minecraft.world.entity.player.Player p) {
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack slot = p.getInventory().getItem(i);
                        if (slot.getItem() instanceof NutrientAgarItem) {
                            slot.shrink(1);
                            hasAgar = true;
                            break;
                        }
                    }
                }
                if (!hasAgar) {
                    player.displayClientMessage(
                        Component.literal("§c[Transfer] Requires Nutrient Agar in inventory."), true);
                    return InteractionResultHolder.fail(dishStack);
                }

                // Copy culture data from main-hand dish to off-hand dish
                CompoundTag sourceTag = NbtHelper.getTag(dishStack);
                NbtHelper.updateTag(otherStack, tag -> {
                    tag.putString(NBT_STATE, DishState.READY.name());
                    tag.putLongArray(NBT_STRAINS, sourceTag.getLongArray(NBT_STRAINS));
                    tag.putBoolean(NBT_MUTATED, sourceTag.getBoolean(NBT_MUTATED));
                    // Transferred copies are NOT marked as irradiated/recombinant originals
                });

                player.displayClientMessage(
                    Component.literal("§a[Transfer] Culture propagated to new dish."), true);
            }
            return InteractionResultHolder.sidedSuccess(dishStack, level.isClientSide);
        }

        // ── Inoculate empty dish from contaminated swab (off-hand) ──
        if (state == DishState.EMPTY && SwabItem.isContaminated(otherStack)) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                long[] strainIds = NbtHelper.getTag(otherStack).getLongArray(SwabItem.NBT_STRAIN_IDS);
                MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
                boolean anyCulturable = false;
                for (long id : strainIds) {
                    MicrobeStrain s = registry.getStrain(id);
                    if (s != null && s.type.culturable) { anyCulturable = true; break; }
                }
                if (!anyCulturable) {
                    player.displayClientMessage(
                        Component.literal("These organisms cannot be cultured in a Petri Dish.").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(dishStack);
                }
                NbtHelper.updateTag(dishStack, tag -> {
                    tag.putString(NBT_STATE, DishState.CULTURING.name());
                    tag.putLongArray(NBT_STRAINS, strainIds);
                    tag.putLong(NBT_START_TICK, serverLevel.getGameTime());
                    tag.putInt(NBT_DURATION, 400); // 400 ticks = 20 real seconds
                });
                otherStack.shrink(1);
                player.displayClientMessage(
                    Component.literal("Culture started. Check back in ~5 minutes.").withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.sidedSuccess(dishStack, level.isClientSide);
        }
        if (state == DishState.CULTURING)
            player.displayClientMessage(Component.literal("Still incubating...").withStyle(ChatFormatting.YELLOW), true);
        if (state == DishState.READY)
            player.displayClientMessage(Component.literal("Culture ready. Place in Microscope or Centrifuge.").withStyle(ChatFormatting.GREEN), true);

        return InteractionResultHolder.pass(dishStack);
    }

    public static void tickCulturing(ItemStack stack, ServerLevel level) {
        if (!(stack.getItem() instanceof PetriDishItem)) return;
        if (getState(stack) != DishState.CULTURING) return;

        CompoundTag tag = NbtHelper.getTag(stack);
        long start = tag.getLong(NBT_START_TICK);
        int duration = tag.getInt(NBT_DURATION);
        if (level.getGameTime() - start < duration) return;

        long[] ids = tag.getLongArray(NBT_STRAINS);
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        Random rng = new Random(level.getSeed() ^ level.getGameTime());
        boolean mutated = false;

        for (int i = 0; i < ids.length; i++) {
            MicrobeStrain parent = registry.getStrain(ids[i]);
            if (parent != null && rng.nextFloat() < parent.type.baseMutationRate * 3) {
                MicrobeStrain child = parent.mutate(parent.strainId ^ rng.nextLong());
                registry.addStrain(child);
                ids[i] = child.strainId;
                mutated = true;
            }
        }
        final long[] finalIds = ids;
        final boolean finalMutated = mutated;
        NbtHelper.updateTag(stack, t -> {
            t.putString(NBT_STATE, DishState.READY.name());
            t.putLongArray(NBT_STRAINS, finalIds);
            t.putBoolean(NBT_MUTATED, finalMutated);
        });
    }

    public static DishState getState(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return DishState.EMPTY;
        CompoundTag tag = NbtHelper.getTag(stack);
        if (!tag.contains(NBT_STATE)) return DishState.EMPTY;
        return DishState.valueOf(tag.getString(NBT_STATE));
    }

    public static long[] getStrainIds(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return new long[0];
        return NbtHelper.getTag(stack).getLongArray(NBT_STRAINS);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        DishState state = getState(stack);
        lines.add(Component.literal("§7State: §f" + state.name()));
        switch (state) {
            case EMPTY -> lines.add(Component.literal("§7Hold off-hand contaminated Swab, right-click to inoculate."));
            case CULTURING -> lines.add(Component.literal("§7Culture in progress (~5 min)."));
            case READY -> {
                lines.add(Component.literal("§7Strains cultured: §f" + getStrainIds(stack).length));
                if (NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).getBoolean(NBT_MUTATED))
                    lines.add(Component.literal("§d⚠ Mutation detected during culture!"));
                lines.add(Component.literal("§7View in Microscope or isolate in Centrifuge."));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) { return getState(stack) == DishState.READY; }
}
