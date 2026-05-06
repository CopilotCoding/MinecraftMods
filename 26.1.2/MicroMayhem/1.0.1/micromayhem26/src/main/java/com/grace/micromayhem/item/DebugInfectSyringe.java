package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                Component.literal("§c[Debug Syringe] Creative mode only."));
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
            List<MicrobeStrain> allStrains = new ArrayList<>(registry.getAllStrains());
            if (allStrains.isEmpty()) {
                player.sendSystemMessage(
                    Component.literal("§c[Debug Syringe] No strains in registry yet."));
                return InteractionResult.FAIL;
            }

            // Check for nearby mob first (within 4 blocks)
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(4),
                e -> e != player);

            MicrobeStrain strain = allStrains.get(player.getRandom().nextInt(allStrains.size()));
            if (strain == null) return InteractionResult.FAIL;

            if (!nearby.isEmpty()) {
                // Infect nearest mob
                LivingEntity target = nearby.get(0);
                MobColony colony = MobColony.load(target);
                boolean infected = colony.tryInfect(strain);
                colony.save(target);
                player.sendSystemMessage(Component.literal(
                    infected
                        ? "§a[Debug Syringe] Injected §6" + strain.getScientificName() + "§a into " + target.getName().getString()
                        : "§e[Debug Syringe] " + target.getName().getString() + " already carries that strain."));
            } else {
                // Infect self via circulatory system
                HostMicrobiome microbiome = new HostMicrobiome();
                microbiome.loadFromTag(player.getPersistentData());
                microbiome.injectDirect(strain, BodySystem.CIRCULATORY, registry);
                microbiome.saveToTag(player.getPersistentData());
                player.sendSystemMessage(Component.literal(
                    "§a[Debug Syringe] Injected §6" + strain.getScientificName()
                    + "§a (§7" + strain.effect.displayName + "§a) into circulatory system."));
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("§5Creative mode only").withStyle(ChatFormatting.ITALIC));
        lines.accept(Component.literal("§7Right-click: inject random strain into self"));
        lines.accept(Component.literal("§7Right-click near mob: inject into mob"));
    }
}
