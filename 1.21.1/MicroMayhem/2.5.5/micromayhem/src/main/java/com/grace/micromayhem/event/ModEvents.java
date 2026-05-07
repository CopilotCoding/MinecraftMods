package com.grace.micromayhem.event;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.item.HazmatArmorItem;
import com.grace.micromayhem.microbe.MobColony;
import com.grace.micromayhem.microbe.SpreadVector;
import com.grace.micromayhem.microbe.BlockContaminationData;
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

        // Tick petri dishes, mob infections, and block contamination once per second
        if (level.getGameTime() % 20 == 0) {
            for (Player player : level.players()) {
                tickPlayerInventoryDishes(player, level);
            }
            // Tick block contamination decay
            com.grace.micromayhem.microbe.BlockContaminationData.get(level).tick();

            // Tick all nearby mob infections (not just pending)
            java.util.Set<LivingEntity> toTick = new java.util.HashSet<>();
            for (Player player : level.players()) {
                level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(48),
                    e -> !(e instanceof Player))
                .forEach(toTick::add);
            }
            for (LivingEntity mob : toTick) {
                // Only tick if they have colony data or pending infections
                if (mob.getPersistentData().contains("MicroMayhem_MobColony")
                        || mob.getPersistentData().contains("MM_PendingInfections")) {
                    tickMobInfections(mob, level);
                }
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

        // Death from illness — skip in creative
        if (result.deathReason != null && !player.isCreative()) {
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

        // Contaminated suit sheds onto nearby entities and blocks (every 10s)
        if (isWearingFullHazmat(player) && player.tickCount % 200 == 0) {
            tickSuitOutboundShedding(player, serverLevel, registry);
        }

        // Sickness particles based on active/severe colony state
        int playerActive = 0, playerSevere = 0;
        for (BodySystem sys : BodySystem.values()) {
            SystemColony col = microbiome.getSystem(sys);
            for (long id : col.getAllStrainIds()) {
                MicrobeStrain s = registry.getStrain(id);
                if (s == null) continue;
                SystemColony.InfectionStage stage = col.getStage(id);
                if (stage == SystemColony.InfectionStage.ACTIVE)
                    playerActive = Math.max(playerActive, s.virulence);
                else if (stage == SystemColony.InfectionStage.SEVERE)
                    playerSevere = Math.max(playerSevere, s.virulence);
            }
        }
        com.grace.micromayhem.client.SicknessParticles.tickPlayer(
            player, serverLevel, playerActive, playerSevere, serverLevel.getGameTime());

        // Save state
        saveMicrobiome(player, microbiome);
        saveImmune(player, immune);
    }

    // Seed newly spawned mobs with biome-appropriate strains
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        // 20% chance a newly spawned mob carries a local strain
        if (mob.getRandom().nextFloat() > 0.20f) return;
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        java.util.List<MicrobeStrain> candidates =
            com.grace.micromayhem.microbe.MicrobePresenceHelper.getStrainForBlock(level, mob.blockPosition());
        if (candidates.isEmpty()) return;
        MicrobeStrain strain = candidates.get(mob.getRandom().nextInt(candidates.size()));
        MobColony colony = MobColony.load(mob);
        if (colony.tryInfect(strain)) colony.save(mob);
    }

    // ---- Sneak + right-click (reserved for future use) ----
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Machines now use proper GUI containers — no sneak+click needed
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

    private static void tickSuitOutboundShedding(Player player, ServerLevel level,
                                                   MicrobeRegistry registry) {
        // Collect all strains absorbed by the suit
        java.util.Set<Long> suitStrains = new java.util.HashSet<>();
        net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET
        };
        for (net.minecraft.world.entity.EquipmentSlot slot : armorSlots) {
            net.minecraft.world.item.ItemStack piece = player.getItemBySlot(slot);
            if (!(piece.getItem() instanceof HazmatArmorItem)) continue;
            if (HazmatArmorItem.getContaminationLevel(piece) == HazmatArmorItem.ContaminationLevel.STERILE) continue;
            for (long id : HazmatArmorItem.getAbsorbedStrains(piece)) suitStrains.add(id);
        }
        if (suitStrains.isEmpty()) return;

        // Shed onto nearby entities based on strain vector
        for (long strainId : suitStrains) {
            MicrobeStrain strain = registry.getStrain(strainId);
            if (strain == null) continue;

            float radius = strain.spreadVectors.contains(SpreadVector.AEROSOL)
                || strain.spreadVectors.contains(SpreadVector.AIRBORNE) ? 6f : 2f;

            // Infect nearby mobs
            for (net.minecraft.world.entity.LivingEntity nearby :
                    level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    player.getBoundingBox().inflate(radius), e -> e != player)) {
                if (nearby instanceof net.minecraft.server.level.ServerPlayer nearbyPlayer) {
                    // Infect other players via HostMicrobiome
                    HostMicrobiome m = new HostMicrobiome();
                    m.loadFromTag(nearbyPlayer.getPersistentData());
                    PlayerImmuneSystem im = new PlayerImmuneSystem();
                    im.loadFromTag(nearbyPlayer.getPersistentData());
                    if (m.tryInfect(strain, 0f, im, registry)) {
                        m.saveToTag(nearbyPlayer.getPersistentData());
                        im.saveToTag(nearbyPlayer.getPersistentData());
                    }
                } else {
                    MobColony colony = MobColony.load(nearby);
                    if (colony.tryInfect(strain)) colony.save(nearby);
                }
            }

            // Shed onto blocks underfoot
            com.grace.micromayhem.microbe.BlockContaminationData.get(level)
                .shed(player.blockPosition(), strainId);
        }
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

    // ---- Mob infection tick ----
    private static void tickMobInfections(LivingEntity mob, ServerLevel level) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        com.grace.micromayhem.microbe.MobColony colony =
            com.grace.micromayhem.microbe.MobColony.load(mob);

        // Also process old pending infections from the syringe system
        net.minecraft.nbt.CompoundTag mobData = mob.getPersistentData();
        net.minecraft.nbt.ListTag pending = mobData.getList(
            "MM_PendingInfections", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (!pending.isEmpty()) {
            net.minecraft.nbt.ListTag remaining = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < pending.size(); i++) {
                net.minecraft.nbt.CompoundTag inf = pending.getCompound(i);
                int ticks = inf.getInt("TicksUntilActive") - 20;
                if (ticks <= 0) {
                    long strainId = inf.getLong("StrainId");
                    MicrobeStrain strain = registry.getStrain(strainId);
                    if (strain != null) colony.tryInfect(strain);
                } else {
                    inf.putInt("TicksUntilActive", ticks);
                    remaining.add(inf);
                }
            }
            if (remaining.isEmpty()) mobData.remove("MM_PendingInfections");
            else mobData.put("MM_PendingInfections", remaining);
        }

        if (colony.isEmpty()) return;

        // Tick colony — returns true if mob should die
        boolean shouldDie = colony.tick(mob, registry);
        if (shouldDie) {
            mob.hurt(level.damageSources().magic(), mob.getMaxHealth());
            colony.save(mob);
            return;
        }

        // Calculate severity for particle effects
        int activeSeverity = 0, severeSeverity = 0;
        for (com.grace.micromayhem.microbe.MobColony.StrainEntry entry : colony.entries()) {
            MicrobeStrain s = registry.getStrain(entry.strainId);
            if (s == null) continue;
            if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.ACTIVE) {
                activeSeverity = Math.max(activeSeverity, s.virulence);
            } else if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.SEVERE) {
                severeSeverity = Math.max(severeSeverity, s.virulence);
            }
        }
        com.grace.micromayhem.client.SicknessParticles.tickMob(
            mob, level, activeSeverity, severeSeverity, level.getGameTime());

        // Mob-to-mob transmission (aerosol + contact)
        net.minecraft.util.RandomSource rng = mob.getRandom();
        for (com.grace.micromayhem.microbe.MobColony.StrainEntry entry : colony.entries()) {
            if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.LATENT)
                continue;
            MicrobeStrain strain = registry.getStrain(entry.strainId);
            if (strain == null) continue;

            float chance = com.grace.micromayhem.microbe.MobColony.transmissionChance(strain);

            // Aerosol — 6 block radius to other mobs
            if (strain.spreadVectors.contains(SpreadVector.AIRBORNE)
                    || strain.spreadVectors.contains(SpreadVector.AEROSOL)) {
                level.getEntitiesOfClass(LivingEntity.class,
                    mob.getBoundingBox().inflate(6),
                    e -> e != mob && !(e instanceof Player))
                .forEach(nearby -> {
                    if (rng.nextFloat() < chance * 0.5f) {
                        com.grace.micromayhem.microbe.MobColony nearbyColony =
                            com.grace.micromayhem.microbe.MobColony.load(nearby);
                        if (nearbyColony.tryInfect(strain)) {
                            nearbyColony.save(nearby);
                            com.grace.micromayhem.client.SicknessParticles.sneezeTransmission(mob, level);
                        }
                    }
                });
            }

            // Contact — 2 block radius
            if (strain.spreadVectors.contains(SpreadVector.CONTACT)) {
                level.getEntitiesOfClass(LivingEntity.class,
                    mob.getBoundingBox().inflate(2),
                    e -> e != mob && !(e instanceof Player))
                .forEach(nearby -> {
                    if (rng.nextFloat() < chance) {
                        com.grace.micromayhem.microbe.MobColony nearbyColony =
                            com.grace.micromayhem.microbe.MobColony.load(nearby);
                        if (nearbyColony.tryInfect(strain)) nearbyColony.save(nearby);
                    }
                });
            }

            // Environment shedding — shed onto nearby blocks at Severe stage
            if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.SEVERE
                    && rng.nextFloat() < 0.15f) {
                com.grace.micromayhem.microbe.BlockContaminationData blockData =
                    com.grace.micromayhem.microbe.BlockContaminationData.get(level);
                net.minecraft.core.BlockPos mobPos = mob.blockPosition();
                // Shed onto block mob is standing on and 1-2 nearby blocks
                blockData.shed(mobPos, strain.strainId);
                blockData.shed(mobPos.below(), strain.strainId);
                if (rng.nextBoolean())
                    blockData.shed(mobPos.offset(rng.nextInt(3)-1, 0, rng.nextInt(3)-1), strain.strainId);
            }
        }

        colony.save(mob);
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

    // When a hazmat piece is newly equipped from empty slot it becomes unsterile
    @SubscribeEvent
    public static void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack incoming = event.getTo();
        ItemStack outgoing = event.getFrom();
        if (!(incoming.getItem() instanceof HazmatArmorItem)) return;
        // Only trigger if the slot was previously empty — i.e. the player just put it on
        // If outgoing is also a hazmat piece the autoclave is just updating NBT, don't change contamination
        if (!outgoing.isEmpty()) return;
        if (HazmatArmorItem.getContaminationLevel(incoming) == HazmatArmorItem.ContaminationLevel.STERILE) {
            HazmatArmorItem.setContaminationLevel(incoming, HazmatArmorItem.ContaminationLevel.LOW);
        }
    }
}
