package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SwabItem extends Item {

    public static final String NBT_STRAIN_IDS  = "StrainIds";
    public static final String NBT_SAMPLE_TYPE = "SampleType";
    public static final String NBT_CONTAMINATED = "Contaminated";

    public SwabItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        if (isContaminated(stack)) {
            player.displayClientMessage(
                Component.literal("Swab already used. Place in Petri Dish or Microscope.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = ctx.getClickedPos();
            List<MicrobeStrain> strains = MicrobePresenceHelper.getStrainForBlock(serverLevel, pos);
            if (strains.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("No detectable microbes on this surface.").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.FAIL;
            }
            // Consume one swab, give back a single contaminated swab
            ItemStack contaminated = new ItemStack(this, 1);
            contaminateStack(contaminated, strains, "block");
            stack.shrink(1);
            if (!player.getInventory().add(contaminated)) {
                player.drop(contaminated, false);
            }
            player.displayClientMessage(
                Component.literal("Sample collected from surface.").withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isContaminated(stack)) return InteractionResultHolder.fail(stack);

        if (level instanceof ServerLevel serverLevel) {
            AABB box = player.getBoundingBox().inflate(3.0);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player);

            List<MicrobeStrain> strains;
            String sampleType;

            if (!nearby.isEmpty()) {
                LivingEntity target = nearby.get(0);
                strains = MicrobePresenceHelper.getStrainForEntity(serverLevel, target);
                sampleType = "entity";
                if (strains.isEmpty()) {
                    player.displayClientMessage(
                        Component.literal("No detectable microbes on this entity.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                player.displayClientMessage(
                    Component.literal("Sample collected from " + target.getName().getString() + ".")
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                strains = MicrobePresenceHelper.getAirborneStrains(serverLevel, player.blockPosition());
                sampleType = "air";
                if (strains.isEmpty()) {
                    player.displayClientMessage(
                        Component.literal("Air sample clear — no detectable aerosols.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.fail(stack);
                }
                player.displayClientMessage(
                    Component.literal("Airborne sample collected.").withStyle(ChatFormatting.GREEN), true);
            }

            // Consume one swab, produce one contaminated swab
            ItemStack contaminated = new ItemStack(this, 1);
            contaminateStack(contaminated, strains, sampleType);
            stack.shrink(1);
            if (!player.getInventory().add(contaminated)) {
                player.drop(contaminated, false);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static boolean isContaminated(ItemStack stack) {
        return NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).getBoolean(NBT_CONTAMINATED);
    }

    public static void contaminateStack(ItemStack stack, List<MicrobeStrain> strains, String type) {
        NbtHelper.updateTag(stack, tag -> {
            tag.putLongArray(NBT_STRAIN_IDS, strains.stream().mapToLong(s -> s.strainId).toArray());
            tag.putString(NBT_SAMPLE_TYPE, type);
            tag.putBoolean(NBT_CONTAMINATED, true);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (isContaminated(stack)) {
            CompoundTag tag = NbtHelper.getTag(stack);
            lines.add(Component.literal("§7[Contaminated — view in Microscope]"));
            lines.add(Component.literal("§7Strains detected: §f" + tag.getLongArray(NBT_STRAIN_IDS).length));
            lines.add(Component.literal("§7Source: §f" + tag.getString(NBT_SAMPLE_TYPE)));
        } else {
            lines.add(Component.literal("§7Right-click surface or mob to sample."));
            lines.add(Component.literal("§7Right-click air for airborne organisms."));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) { return isContaminated(stack); }
}
