package com.grace.micromayhem.event;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.microbe.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MicroMayhem.MODID)
public class MicroMayhemCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("microbiome")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                showMicrobiome(player);
                return 1;
            })
        );

        dispatcher.register(Commands.literal("codex")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                showCodexSummary(player);
                return 1;
            })
            .then(Commands.literal("list").executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                showCodexList(player);
                return 1;
            }))
        );

        dispatcher.register(Commands.literal("straininfo")
            .then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    long id = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id");
                    showStrainInfo(player, id);
                    return 1;
                }))
        );

        dispatcher.register(Commands.literal("immune")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                showImmuneStatus(player);
                return 1;
            })
        );

        dispatcher.register(Commands.literal("debugsuit")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                debugSuit(player);
                return 1;
            })
        );

        dispatcher.register(Commands.literal("forcecontam")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.FEET
                }) {
                    net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                    if (stack.getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem) {
                        com.grace.micromayhem.item.HazmatArmorItem.absorbStrain(stack, 99999L);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§aForce-absorbed strain into " + slot.name() + 
                            " -> strains=" + com.grace.micromayhem.item.HazmatArmorItem.getAbsorbedStrains(stack).length +
                            " contam=" + com.grace.micromayhem.item.HazmatArmorItem.getContaminationLevel(stack).name()));
                    }
                }
                return 1;
            })
        );
    }

    private static void showMicrobiome(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        HostMicrobiome microbiome = ModEvents.loadMicrobiome(player);

        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§6§lMICROBIOME STATUS — " + player.getName().getString()));
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));

        for (BodySystem system : BodySystem.values()) {
            SystemColony colony = microbiome.getSystem(system);
            float health = colony.systemHealth;
            String healthColor = health > 0.6f ? "§a" : health > 0.3f ? "§e" : "§c";
            String failureTag = colony.inFailure ? " §4§l[FAILURE]" : "";
            player.sendSystemMessage(Component.literal(
                "§7§l" + system.displayName + " §7(" +
                healthColor + String.format("%.0f%%", health * 100) + "§7)" + failureTag));

            if (colony.getAllStrainIds().isEmpty()) {
                player.sendSystemMessage(Component.literal("  §8No active strains."));
            } else {
                for (long id : colony.getAllStrainIds()) {
                    MicrobeStrain strain = registry.getStrain(id);
                    if (strain == null) continue;
                    SystemColony.InfectionStage stage = colony.getStage(id);
                    float size = colony.colonySizes.getOrDefault(id, 0f);
                    String stageColor = switch (stage) {
                        case EXPOSED  -> "§7";
                        case LATENT   -> "§e";
                        case ACTIVE   -> "§6";
                        case SEVERE   -> "§c";
                        case CLEARING -> "§a";
                    };
                    String established = colony.establishedStrains.contains(id) ? " §2[EST]" : "";
                    player.sendSystemMessage(Component.literal(
                        "  " + stageColor + "● " + strain.getScientificName() +
                        " §7[" + stage.name() + "] " +
                        String.format("%.0f%%", size * 100) + " colony" + established));
                    player.sendSystemMessage(Component.literal(
                        "    §7Effect: §f" + strain.effect.displayName +
                        " | " + strain.getVirulenceLabel()));
                }
            }
        }
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§7Stable: " +
            (microbiome.isMicrobiomeStable() ? "§a✓ MICROBIOME STABLE" : "§c✗ Unstable")));
    }

    private static void showImmuneStatus(ServerPlayer player) {
        PlayerImmuneSystem immune = ModEvents.loadImmune(player);
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§b§lIMMUNE SYSTEM — " + player.getName().getString()));
        String strengthColor = immune.immuneStrength > 0.6f ? "§a"
            : immune.immuneStrength > 0.3f ? "§e" : "§c";
        player.sendSystemMessage(Component.literal(
            "§7Strength: " + strengthColor + String.format("%.0f%%", immune.immuneStrength * 100) +
            " §7/ Ceiling: §f" + String.format("%.0f%%", immune.immuneStrengthCeiling * 100)));
        player.sendSystemMessage(Component.literal(
            "§7Natural immunities: §f" + immune.naturalImmunity.size()));
        if (immune.autoimmune) {
            player.sendSystemMessage(Component.literal(
                "§d⚠ AUTOIMMUNE CONDITION — Level " + immune.autoimmuneLevel + "/3"));
            player.sendSystemMessage(Component.literal(
                "§7Stable days toward de-escalation: §f" + immune.daysStable + "/7"));
        } else if (immune.autoimmunePrimed) {
            player.sendSystemMessage(Component.literal(
                "§d⚠ Immune irregularity detected (primed — avoid severe infections)"));
        }
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
    }

    private static void showCodexSummary(ServerPlayer player) {
        MicrobeCodex codex = new MicrobeCodex();
        codex.loadFromTag(player.getPersistentData());
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§d§lMICROBE CODEX — " + codex.count() + " strains"));
        player.sendSystemMessage(Component.literal("§7Use §f/codex list §7to see all entries."));
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
    }

    private static void showCodexList(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeCodex codex = new MicrobeCodex();
        codex.loadFromTag(player.getPersistentData());
        player.sendSystemMessage(Component.literal("§d§lCODEX — " + codex.count() + " entries"));
        int i = 1;
        for (long id : codex.getDiscovered()) {
            MicrobeStrain s = registry.getStrain(id);
            if (s == null) continue;
            String cat = switch (s.effect.category) {
                case POSITIVE -> "§a";
                case NEGATIVE -> "§c";
                case NEUTRAL  -> "§e";
            };
            player.sendSystemMessage(Component.literal(
                "§7" + i + ". §6" + s.getScientificName() +
                " §7(" + s.type.displayName + ") " +
                cat + s.effect.displayName +
                " §7[Gen." + s.generation + "] §8ID:" + id));
            i++;
        }
    }

    private static void showStrainInfo(ServerPlayer player, long id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        MicrobeStrain strain = MicrobeRegistry.get(level).getStrain(id);
        if (strain == null) {
            player.sendSystemMessage(Component.literal("§cStrain ID " + id + " not found."));
            return;
        }
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        for (String line : strain.getMicroscopeReadout())
            player.sendSystemMessage(Component.literal(line));
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
    }

    private static void debugSuit(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        MicrobeRegistry registry = MicrobeRegistry.get(level);

        // Check full hazmat
        boolean fullHazmat = 
            player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem;

        player.sendSystemMessage(Component.literal("§e=== Hazmat Suit Debug ==="));
        player.sendSystemMessage(Component.literal("§7Full hazmat worn: §f" + fullHazmat));

        // Check each slot
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET
        }) {
            net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
            String itemName = stack.isEmpty() ? "EMPTY" : stack.getItem().getClass().getSimpleName();
            boolean isHazmat = stack.getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem;
            String contam = isHazmat ? com.grace.micromayhem.item.HazmatArmorItem.getContaminationLevel(stack).name() : "N/A";
            long[] strains = isHazmat ? com.grace.micromayhem.item.HazmatArmorItem.getAbsorbedStrains(stack) : new long[0];
            player.sendSystemMessage(Component.literal("§7" + slot.name() + ": §f" + itemName + " hazmat=" + isHazmat + " contam=" + contam + " strains=" + strains.length));
        }

        // Check nearby mobs with colony data
        int mobCount = 0, sickCount = 0;
        java.util.List<String> strainNames = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.LivingEntity mob :
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(10))) {
            if (mob == player) continue;
            mobCount++;
            if (!mob.getPersistentData().contains("MicroMayhem_MobColony")) continue;
            com.grace.micromayhem.microbe.MobColony colony = com.grace.micromayhem.microbe.MobColony.load(mob);
            if (colony.entries().isEmpty()) continue;
            sickCount++;
            for (com.grace.micromayhem.microbe.MobColony.StrainEntry entry : colony.entries()) {
                MicrobeStrain s = registry.getStrain(entry.strainId);
                strainNames.add((s != null ? s.getScientificName() : "?") + "[" + entry.stage + "]");
            }
        }
        player.sendSystemMessage(Component.literal("§7Mobs within 10: §f" + mobCount + " total, §c" + sickCount + " sick"));
        player.sendSystemMessage(Component.literal("§7Strains: §f" + (strainNames.isEmpty() ? "none" : String.join(", ", strainNames))));
        player.sendSystemMessage(Component.literal("§7GameTime % 100 = §f" + (level.getGameTime() % 100)));
    }

}