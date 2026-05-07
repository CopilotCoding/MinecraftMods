package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class SyringeItem extends Item {

    private static final String NBT_LOADED    = "Loaded";
    private static final String NBT_STRAINS   = "StrainIds";
    static final         String NBT_IS_VACCINE = "IsVaccine";

    public SyringeItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);

        // Load from ready petri dish
        if (otherStack.getItem() instanceof PetriDishItem
                && PetriDishItem.getState(otherStack) == PetriDishItem.DishState.READY
                && !isLoaded(stack)) {
            if (!level.isClientSide) {
                long[] ids = PetriDishItem.getStrainIds(otherStack);
                NbtHelper.updateTag(stack, tag -> {
                    tag.putLongArray(NBT_STRAINS, ids);
                    tag.putBoolean(NBT_LOADED, true);
                    tag.putBoolean(NBT_IS_VACCINE, false);
                });
                player.displayClientMessage(
                    Component.literal("Syringe loaded with culture sample.").withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Self-inject
        if (isLoaded(stack) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            injectEntity(stack, serverLevel, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!isLoaded(stack)) {
            player.displayClientMessage(Component.literal("Syringe is empty.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        Level level = player.level();
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            injectEntity(stack, serverLevel, target);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected void injectEntity(ItemStack stack, ServerLevel level, LivingEntity target) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        CompoundTag tag = NbtHelper.getTag(stack);
        long[] ids = tag.getLongArray(NBT_STRAINS);
        boolean isVaccine = tag.getBoolean(NBT_IS_VACCINE);

        for (long id : ids) {
            MicrobeStrain strain = registry.getStrain(id);
            if (strain == null) continue;

            if (target instanceof Player p) {
                HostMicrobiome microbiome = new HostMicrobiome();
                microbiome.loadFromTag(p.getPersistentData());
                PlayerImmuneSystem immune = new PlayerImmuneSystem();
                immune.loadFromTag(p.getPersistentData());

                if (isVaccine) {
                    immune.naturalImmunity.add(id);
                    immune.saveToTag(p.getPersistentData());
                    target.sendSystemMessage(
                        Component.literal("Vaccinated against " + strain.getScientificName())
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    // Inject directly into circulatory system — most dangerous entry point
                    microbiome.injectDirect(strain, BodySystem.CIRCULATORY, registry);
                    microbiome.saveToTag(p.getPersistentData());
                    int latentSecs = (200 + (int)(Math.random() * 400)) / 20;
                    target.sendSystemMessage(Component.literal(
                        "§c[Infected] " + strain.getScientificName() +
                        " — symptoms in ~" + latentSecs + "s"));
                }
            } else {
                if (isVaccine) continue;
                // Non-player mobs: schedule delayed effect via tag on the entity
                // We apply a short resistance buff first (simulating latent period),
                // then the actual effect fires after the latent delay naturally
                int latentTicks = 200 + (int)(Math.random() * 400); // 10–30s
                // Apply a neutral placeholder effect for the latent window,
                // then the real effect after latency using a two-stage approach:
                // Stage 1: nothing visible (latent)
                // Stage 2: apply wither/poison/etc at full virulence
                // We schedule this by giving the mob a custom tag and checking in ModEvents
                net.minecraft.nbt.CompoundTag mobData = target.getPersistentData();
                net.minecraft.nbt.ListTag pending = mobData.contains("MM_PendingInfections")
                    ? mobData.getList("MM_PendingInfections", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    : new net.minecraft.nbt.ListTag();
                net.minecraft.nbt.CompoundTag inf = new net.minecraft.nbt.CompoundTag();
                inf.putLong("StrainId", id);
                inf.putInt("TicksUntilActive", latentTicks);
                inf.putInt("Duration", strain.effectDurationTicks);
                inf.putInt("Amplifier", strain.virulence - 1);
                pending.add(inf);
                mobData.put("MM_PendingInfections", pending);
                target.sendSystemMessage(Component.literal("§c[Infected] " + strain.getScientificName()));
            }
        }

        NbtHelper.updateTag(stack, t -> {
            t.putBoolean(NBT_LOADED, false);
            t.remove(NBT_STRAINS);
        });
    }

    public static boolean isLoaded(ItemStack stack) {
        return NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).getBoolean(NBT_LOADED);
    }

    public static void markAsVaccine(ItemStack stack) {
        NbtHelper.updateTag(stack, tag -> tag.putBoolean(NBT_IS_VACCINE, true));
    }

    public static long[] getStrainIds(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return new long[0];
        return NbtHelper.getTag(stack).getLongArray(NBT_STRAINS);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (isLoaded(stack)) {
            CompoundTag tag = NbtHelper.getTag(stack);
            boolean vaccine = tag.getBoolean(NBT_IS_VACCINE);
            lines.add(Component.literal("§7Loaded with §f" + tag.getLongArray(NBT_STRAINS).length + " strain(s)"));
            lines.add(Component.literal(vaccine ? "§a[VACCINE SYRINGE]" : "§c[LIVE CULTURE]"));
            lines.add(Component.literal("§7Right-click entity or self to inject."));
        } else {
            lines.add(Component.literal("§7Empty. Load from a ready Petri Dish (off-hand)."));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) { return isLoaded(stack); }

    private static PlayerImmuneSystem loadImmune(Player player) {
        PlayerImmuneSystem immune = new PlayerImmuneSystem();
        immune.loadFromTag(player.getPersistentData());
        return immune;
    }
}
