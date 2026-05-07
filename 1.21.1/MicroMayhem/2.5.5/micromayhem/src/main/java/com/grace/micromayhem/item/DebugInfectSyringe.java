package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative-only debug tool.
 * Right-click: infects yourself with a random strain from the world registry.
 * Right-click on a mob: infects that mob.
 * Uses vaccine syringe texture with enchantment glint.
 * Only usable in creative mode.
 */
public class DebugInfectSyringe extends Item {

    public DebugInfectSyringe(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // always glowing enchantment effect
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isCreative()) {
            player.displayClientMessage(
                Component.literal("§c[Debug Syringe] Creative mode only."), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        if (level instanceof ServerLevel serverLevel) {
            MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
            List<MicrobeStrain> allStrains = new ArrayList<>(registry.getAllStrains());
            if (allStrains.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§c[Debug Syringe] No strains in registry yet."), true);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }

            // Check for nearby mob first (within 4 blocks)
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(4),
                e -> e != player);

            MicrobeStrain strain = allStrains.get(player.getRandom().nextInt(allStrains.size()));
            if (strain == null) return InteractionResultHolder.fail(player.getItemInHand(hand));

            if (!nearby.isEmpty()) {
                LivingEntity target = nearby.get(0);
                if (target instanceof net.minecraft.server.level.ServerPlayer targetPlayer) {
                    // Target is a player — use HostMicrobiome
                    HostMicrobiome targetMicrobiome = new HostMicrobiome();
                    targetMicrobiome.loadFromTag(targetPlayer.getPersistentData());
                    targetMicrobiome.injectDirect(strain, BodySystem.CIRCULATORY, registry);
                    targetMicrobiome.saveToTag(targetPlayer.getPersistentData());
                    player.displayClientMessage(Component.literal(
                        "§a[Debug Syringe] Injected §6" + strain.getScientificName()
                        + "§a into player " + targetPlayer.getName().getString()), true);
                } else {
                    // Target is a mob — use MobColony
                    MobColony colony = MobColony.load(target);
                    boolean infected = colony.tryInfect(strain);
                    colony.save(target);
                    player.displayClientMessage(Component.literal(
                        infected
                            ? "§a[Debug Syringe] Injected §6" + strain.getScientificName() + "§a into " + target.getName().getString()
                            : "§e[Debug Syringe] " + target.getName().getString() + " already carries that strain."), true);
                }
            } else {
                // Infect self via circulatory system
                HostMicrobiome microbiome = new HostMicrobiome();
                microbiome.loadFromTag(player.getPersistentData());
                microbiome.injectDirect(strain, BodySystem.CIRCULATORY, registry);
                microbiome.saveToTag(player.getPersistentData());
                player.displayClientMessage(Component.literal(
                    "§a[Debug Syringe] Injected §6" + strain.getScientificName()
                    + "§a (§7" + strain.effect.displayName + "§a) into circulatory system."), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("§5Creative mode only").withStyle(ChatFormatting.ITALIC));
        lines.add(Component.literal("§7Right-click: inject random strain into self"));
        lines.add(Component.literal("§7Right-click near mob: inject into mob"));
    }
}
