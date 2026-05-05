package com.grace.micromayhem.event;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.blockentity.IrradiatorBlockEntity;
import com.grace.micromayhem.item.HazmatArmorItem;
import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = MicroMayhem.MODID)
public class ModEvents {

    // ---- World load ----
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == ServerLevel.OVERWORLD) {
            MicrobeRegistry registry = MicrobeRegistry.get(level);
            registry.init(level.getSeed());
            // Ensure world strain exists
            registry.getOrCreateWorldStrain();
            MicroMayhem.LOGGER.info("[MicroMayhem] Registry initialised. {} strains.",
                registry.getAllStrains().size());
        }
    }

    // ---- Level tick ----
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != ServerLevel.OVERWORLD) return;

        long dayTime = level.getDayTime();
        long currentDay = dayTime / 24000L;
        if (dayTime % 24000L == 6000L) {
            MicrobeRegistry.get(level).onDayPassed(currentDay);
        }

        // Tick petri dishes and mob infections once per second
        if (level.getGameTime() % 20 == 0) {
            for (Player player : level.players()) {
                tickPlayerInventoryDishes(player, level);
            }
            // Mob pending infections
            for (Player player : level.players()) {
                level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(32),
                    e -> !(e instanceof Player) &&
                         e.getPersistentData().contains("MM_PendingInfections"))
                .forEach(mob -> tickMobInfections(mob, level));
            }
        }
    }

    // ---- Player tick ----
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (player.tickCount % 20 != 0) return; // once per second

        MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
        HostMicrobiome microbiome = loadMicrobiome(player);
        PlayerImmuneSystem immune = loadImmune(player);

        // Roll susceptibility on first tick
        immune.rollSusceptibility(player);

        // Run colony simulation
        ColonySimulator.TickResult result = ColonySimulator.tick(
            player, microbiome, immune, registry, serverLevel);

        // Handle clearances
        for (long strainId : result.clearingStrains) {
            immune.onNaturalClearance(strainId, serverLevel.getGameTime());
            microbiome.clearStrain(strainId, registry);
        }

        // Handle autoimmune candidates
        for (ColonySimulator.TickResult.AutoimmuneCandidate candidate : result.autoimmuneCandidates) {
            boolean triggered = immune.checkAutoimmuneTrigger(
                candidate.strain, serverLevel.getGameTime());
            if (triggered) notifyAutoimmune(player, immune, candidate.strain);
        }

        // Handle migrations
        for (ColonySimulator.TickResult.Migration migration : result.migrations) {
            microbiome.migrateStrain(migration.strainId, migration.from, migration.to, registry);
            player.sendSystemMessage(Component.literal(
                "§c[Infection] " + migration.from.displayName +
                " infection has spread to " + migration.to.displayName + " system!"));
        }

        // Autoimmune misfire
        if (result.autoimmuneMisfire && result.misfireEffect != null
                && !isWearingFullHazmat(player)) {
            if (result.misfireEffect.hasVanillaEffect()) {
                player.addEffect(new MobEffectInstance(
                    result.misfireEffect.vanillaEffect,
                    200, result.misfireVirulence - 1));
            }
            player.sendSystemMessage(Component.literal(
                "§d[Autoimmune] Immune system misfiring — " + result.misfireEffect.displayName));
        }

        // Death from illness
        if (result.deathReason != null) {
            player.hurt(serverLevel.damageSources().magic(), Float.MAX_VALUE);
            player.sendSystemMessage(Component.literal(
                "§4§lDeath from illness: " + result.deathReason));
        }

        // Microbiome stable bonus
        if (result.microbiomeStable) {
            player.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 40, 0, false, false));
        }

        // Immune strength tick
        immune.onDayTick(result.microbiomeStable);

        // Run transmission system (block, water, airborne, aerosol, contact)
        TransmissionSystem.tick(player, serverLevel, microbiome, immune, registry);

        // Hazmat inventory exposure (only if not wearing full suit)
        if (!isWearingFullHazmat(player)) {
            tickHazmatInventoryExposure(player, serverLevel, microbiome, immune, registry);
        }

        // Save state
        saveMicrobiome(player, microbiome);
        saveImmune(player, immune);
    }

    // ---- Sneak + right-click ----
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        net.minecraft.core.BlockPos pos = event.getPos();
        if (level.getBlockEntity(pos) instanceof IrradiatorBlockEntity irradiator) {
            irradiator.startCycle(serverPlayer);
            event.setCanceled(true);
        } else if (level.getBlockEntity(pos) instanceof com.grace.micromayhem.blockentity.GeneSplicerBlockEntity splicer) {
            splicer.startSplice(serverPlayer);
            event.setCanceled(true);
        }
    }

    // ---- Player login ----
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel serverLevel) {
            MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
            HostMicrobiome microbiome = loadMicrobiome(player);
            PlayerImmuneSystem immune = loadImmune(player);
            immune.rollSusceptibility(player);
            saveMicrobiome(player, microbiome);
            saveImmune(player, immune);
        }
    }

    // ---- Hazmat suit helpers ----

    public static boolean isWearingFullHazmat(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HazmatArmorItem
            && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof HazmatArmorItem
            && player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof HazmatArmorItem
            && player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof HazmatArmorItem;
    }

    private static void tickHazmatInventoryExposure(Player player, ServerLevel level,
            HostMicrobiome microbiome, PlayerImmuneSystem immune, MicrobeRegistry registry) {
        // Only tick every 60 seconds
        if (player.tickCount % 1200 != 0) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof HazmatArmorItem)) continue;
            if (!HazmatArmorItem.isContaminated(stack)) continue;

            long[] absorbed = HazmatArmorItem.getAbsorbedStrains(stack);
            HazmatArmorItem.ContaminationLevel level2 = HazmatArmorItem.getContaminationLevel(stack);
            float exposureRate = level2 == HazmatArmorItem.ContaminationLevel.HAZARDOUS ? 0.6f : 0.3f;

            for (long strainId : absorbed) {
                MicrobeStrain strain = registry.getStrain(strainId);
                if (strain != null) {
                    microbiome.tryInfect(strain, exposureRate - strain.transmissibility, immune, registry);
                }
            }

            if (absorbed.length > 0) {
                player.sendSystemMessage(Component.literal(
                    "§c[Hazmat] Contaminated suit in inventory is exposing you to absorbed organisms!"));
            }
        }
    }

    // ---- Autoimmune notification ----
    private static void notifyAutoimmune(Player player, PlayerImmuneSystem immune, MicrobeStrain strain) {
        if (immune.autoimmune) {
            player.sendSystemMessage(Component.literal(
                "§d§l[AUTOIMMUNE] Condition worsened to Level " + immune.autoimmuneLevel +
                ". Maintain a stable microbiome to suppress."));
        } else if (immune.autoimmunePrimed) {
            player.sendSystemMessage(Component.literal(
                "§d[Warning] Immune system irregularity detected. " +
                "Avoid further severe infections for 30 days."));
        }
    }

    // ---- Mob pending infection tick ----
    private static void tickMobInfections(LivingEntity mob, ServerLevel level) {
        net.minecraft.nbt.CompoundTag mobData = mob.getPersistentData();
        net.minecraft.nbt.ListTag pending = mobData.getList(
            "MM_PendingInfections", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (pending.isEmpty()) { mobData.remove("MM_PendingInfections"); return; }

        MicrobeRegistry registry = MicrobeRegistry.get(level);
        net.minecraft.nbt.ListTag remaining = new net.minecraft.nbt.ListTag();

        for (int i = 0; i < pending.size(); i++) {
            net.minecraft.nbt.CompoundTag inf = pending.getCompound(i);
            int ticks = inf.getInt("TicksUntilActive") - 20;
            if (ticks <= 0) {
                long strainId = inf.getLong("StrainId");
                MicrobeStrain strain = registry.getStrain(strainId);
                if (strain != null && strain.effect.hasVanillaEffect()) {
                    mob.addEffect(new MobEffectInstance(
                        strain.effect.vanillaEffect,
                        inf.getInt("Duration"),
                        inf.getInt("Amplifier")));
                }
            } else {
                inf.putInt("TicksUntilActive", ticks);
                remaining.add(inf);
            }
        }

        if (remaining.isEmpty()) mobData.remove("MM_PendingInfections");
        else mobData.put("MM_PendingInfections", remaining);
    }

    // ---- Petri dish tick ----
    private static void tickPlayerInventoryDishes(Player player, ServerLevel level) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PetriDishItem) {
                PetriDishItem.tickCulturing(stack, level);
            }
        }
    }

    // ---- Food consumption — expose to raw food strains ----
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (isWearingFullHazmat(player)) return;

        ItemStack item = event.getItem();
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        HostMicrobiome microbiome = loadMicrobiome(player);
        PlayerImmuneSystem immune = loadImmune(player);

        // Raw food exposure
        if (TransmissionSystem.isRawFood(item)) {
            List<MicrobeStrain> strains = TransmissionSystem.getFoodStrains(
                item, level, player.blockPosition(), registry);
            boolean exposed = false;
            for (MicrobeStrain strain : strains) {
                boolean infected = microbiome.tryInfect(strain, 0.1f, immune, registry);
                if (infected) exposed = true;
            }
            if (exposed) {
                player.sendSystemMessage(Component.literal(
                    "§e[Food] You may have ingested something with this meal..."));
            }
        }

        // Water drinking — bucket or bottle
        boolean isWaterDrink = item.is(net.minecraft.world.item.Items.WATER_BUCKET)
            || item.is(net.minecraft.world.item.Items.POTION); // water bottles are potions internally
        if (isWaterDrink) {
            List<MicrobeStrain> strains = TransmissionSystem.getDrinkingWaterStrains(
                level, player.blockPosition(), registry);
            for (MicrobeStrain strain : strains) {
                microbiome.tryInfect(strain, 0.0f, immune, registry);
            }
        }

        saveMicrobiome(player, microbiome);
        saveImmune(player, immune);
    }

    // ---- Hazmat absorption — absorb strains on blocked transmissions ----
    // This is handled passively in TransmissionSystem but we also hook it here
    // for food/water vectors that bypass the tick

    // ---- State helpers ----
    public static HostMicrobiome loadMicrobiome(Player player) {
        HostMicrobiome m = new HostMicrobiome();
        m.loadFromTag(player.getPersistentData());
        return m;
    }

    public static void saveMicrobiome(Player player, HostMicrobiome m) {
        m.saveToTag(player.getPersistentData());
    }

    public static PlayerImmuneSystem loadImmune(Player player) {
        PlayerImmuneSystem immune = new PlayerImmuneSystem();
        immune.loadFromTag(player.getPersistentData());
        return immune;
    }

    public static void saveImmune(Player player, PlayerImmuneSystem immune) {
        immune.saveToTag(player.getPersistentData());
    }
}
