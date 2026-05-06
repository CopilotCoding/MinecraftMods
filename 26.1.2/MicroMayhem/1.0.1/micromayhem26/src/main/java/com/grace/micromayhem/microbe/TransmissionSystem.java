package com.grace.micromayhem.microbe;

import com.grace.micromayhem.item.HazmatArmorItem;

import com.grace.micromayhem.microbe.BlockContaminationData;
import com.grace.micromayhem.microbe.MobColony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles all six transmission vectors fully.
 *
 * Called from ModEvents once per second (tick-gated internally).
 *
 * AIRBORNE  — already working, refined here
 * AEROSOL   — infected nearby mobs emit, tighter radius
 * CONTACT   — entity-to-entity proximity spread
 * SOIL/STONE/WOOD — standing on block 5s → roll → reset
 * WATER     — swimming roll every 30s
 * FOOD      — handled via event hooks (LivingEntityUseItemEvent)
 */
public class TransmissionSystem {

    // NBT keys stored in player persistent data
    private static final String NBT_BLOCK_STAND_TICKS  = "MM_BlockStandTicks";
    private static final String NBT_BLOCK_STAND_TYPE   = "MM_BlockStandType";
    private static final String NBT_LAST_WATER_TICK    = "MM_LastWaterTick";

    // Block exposure threshold
    private static final int STAND_TICKS_REQUIRED = 100; // 5 seconds

    /**
     * Main tick entry point — call every second from ModEvents.
     * Handles soil/stone/wood, water swimming, aerosol from nearby mobs.
     */
    public static void tick(Player player, ServerLevel level,
                            HostMicrobiome microbiome, PlayerImmuneSystem immune,
                            MicrobeRegistry registry) {

        // Full hazmat — absorb strains from environment instead of infecting player
        if (isFullHazmat(player)) {
            absorbEnvironmentalStrains(player, level, registry);
            return;
        }

        BlockPos feet = player.blockPosition();
        BlockPos below = feet.below();

        // ---- 1. Block surface exposure (soil/stone/wood) ----
        tickBlockExposure(player, level, below, microbiome, immune, registry);

        // ---- 2. Water swimming exposure (every 30s) ----
        if (player.isInWater() && level.getGameTime() % 600 == 0) {
            tickWaterExposure(player, level, feet, microbiome, immune, registry);
        }

        // ---- 3. Airborne exposure (every 10s) ----
        if (level.getGameTime() % 200 == 0) {
            tickAirborneExposure(player, level, feet, microbiome, immune, registry);
        }

        // ---- 4. Aerosol from infected nearby mobs (every 5s) ----
        if (level.getGameTime() % 100 == 0) {
            tickAerosolExposure(player, level, microbiome, immune, registry);
        }

        // ---- 5. Contact spread from nearby entities (every 10s) ----
        if (level.getGameTime() % 200 == 0) {
            tickContactExposure(player, level, microbiome, immune, registry);
        }
    }

    // ---- SOIL / STONE / WOOD ----

    private static void tickBlockExposure(Player player, ServerLevel level, BlockPos below,
                                          HostMicrobiome microbiome, PlayerImmuneSystem immune,
                                          MicrobeRegistry registry) {
        BlockState state = level.getBlockState(below);
        String blockType = classifyBlock(state);
        if (blockType == null) {
            // Not a relevant block — reset timer
            player.getPersistentData().putInt(NBT_BLOCK_STAND_TICKS, 0);
            player.getPersistentData().putString(NBT_BLOCK_STAND_TYPE, "");
            return;
        }

        String lastType = player.getPersistentData().getStringOr(NBT_BLOCK_STAND_TYPE, "");
        int ticks = player.getPersistentData().getIntOr(NBT_BLOCK_STAND_TICKS, 0);

        // Reset if block type changed
        if (!blockType.equals(lastType)) {
            ticks = 0;
            player.getPersistentData().putString(NBT_BLOCK_STAND_TYPE, blockType);
        }

        ticks += 20; // +1 second per tick call
        player.getPersistentData().putInt(NBT_BLOCK_STAND_TICKS, ticks);

        if (ticks >= STAND_TICKS_REQUIRED) {
            // Roll exposure and reset timer
            player.getPersistentData().putInt(NBT_BLOCK_STAND_TICKS, 0);

            List<MicrobeStrain> candidates = MicrobePresenceHelper.getStrainForBlock(level, below);

            // Also check dynamically contaminated blocks (shed by infected mobs)
            BlockContaminationData blockContam = BlockContaminationData.get(level);
            for (long id : blockContam.getStrains(below)) {
                MicrobeStrain strain = registry.getStrain(id);
                if (strain != null && !candidates.contains(strain)) candidates.add(strain);
            }
            // Also check the block the player is standing on
            for (long id : blockContam.getStrains(below.above())) {
                MicrobeStrain strain = registry.getStrain(id);
                if (strain != null && !candidates.contains(strain)) candidates.add(strain);
            }

            for (MicrobeStrain strain : candidates) {
                SpreadVector required = vectorForBlockType(blockType);
                if (strain.spreadVectors.contains(required) || blockContam.isContaminated(below)) {
                    microbiome.tryInfect(strain, -0.4f, immune, registry);
                }
            }
        }
    }

    private static String classifyBlock(BlockState state) {
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) ||
            state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) ||
            state.is(Blocks.CLAY) || state.is(Blocks.COARSE_DIRT) ||
            state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) ||
            state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD)) {
            return "SOIL";
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) ||
            state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF) ||
            state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) ||
            state.is(Blocks.ANDESITE) || state.is(Blocks.CALCITE)) {
            return "STONE";
        }
        if (state.is(Blocks.OAK_LOG) || state.is(Blocks.BIRCH_LOG) ||
            state.is(Blocks.SPRUCE_LOG) || state.is(Blocks.JUNGLE_LOG) ||
            state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.MANGROVE_LOG) ||
            state.is(Blocks.OAK_PLANKS) || state.is(Blocks.MUSHROOM_STEM) ||
            state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)) {
            return "WOOD";
        }
        return null;
    }

    private static SpreadVector vectorForBlockType(String type) {
        return switch (type) {
            case "SOIL"  -> SpreadVector.SOIL;
            case "STONE" -> SpreadVector.STONE;
            case "WOOD"  -> SpreadVector.WOOD;
            default      -> SpreadVector.SOIL;
        };
    }

    // ---- WATER SWIMMING ----

    private static void tickWaterExposure(Player player, ServerLevel level, BlockPos pos,
                                           HostMicrobiome microbiome, PlayerImmuneSystem immune,
                                           MicrobeRegistry registry) {
        List<MicrobeStrain> candidates = MicrobePresenceHelper.getStrainForBlock(level, pos);
        for (MicrobeStrain strain : candidates) {
            if (strain.spreadVectors.contains(SpreadVector.WATER)) {
                microbiome.tryInfect(strain, -0.3f, immune, registry);
            }
        }
        // Also check biome waterborne strains
        BiomePressure pressure = BiomePressure.forPosition(level, pos);
        if (pressure == BiomePressure.MOISTURE || pressure == BiomePressure.AQUATIC) {
            MicrobeStrain biomeStrain = registry.getStrainForSeed(
                level.getSeed() ^ pos.asLong() ^ 0xBA5E1BAL);
            if (biomeStrain.spreadVectors.contains(SpreadVector.WATER)) {
                microbiome.tryInfect(biomeStrain, -0.2f, immune, registry);
            }
        }
    }

    // ---- AIRBORNE ----

    private static void tickAirborneExposure(Player player, ServerLevel level, BlockPos pos,
                                              HostMicrobiome microbiome, PlayerImmuneSystem immune,
                                              MicrobeRegistry registry) {
        List<MicrobeStrain> strains = MicrobePresenceHelper.getAirborneStrains(level, pos);
        BiomePressure pressure = BiomePressure.forPosition(level, pos);

        for (MicrobeStrain strain : strains) {
            // Biome pressure modifies airborne chance
            float biomeMod = pressure.getGrowthMod(strain.type) - 1.0f; // -1 to normalize
            microbiome.tryInfect(strain, -0.35f + biomeMod * 0.1f, immune, registry);
        }
    }

    // ---- AEROSOL FROM INFECTED MOBS ----

    private static void tickAerosolExposure(Player player, ServerLevel level,
                                             HostMicrobiome microbiome, PlayerImmuneSystem immune,
                                             MicrobeRegistry registry) {
        level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
            player.getBoundingBox().inflate(6.0),
            e -> e != player && e.getPersistentData().contains("MicroMayhem_MobColony"))
        .forEach(mob -> {
            com.grace.micromayhem.microbe.MobColony colony =
                com.grace.micromayhem.microbe.MobColony.load(mob);
            for (com.grace.micromayhem.microbe.MobColony.StrainEntry entry : colony.entries()) {
                if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.LATENT)
                    continue;
                MicrobeStrain strain = registry.getStrain(entry.strainId);
                if (strain == null) continue;
                if (strain.spreadVectors.contains(SpreadVector.AEROSOL) ||
                    strain.spreadVectors.contains(SpreadVector.AIRBORNE)) {
                    double dist = player.distanceTo(mob);
                    float distPenalty = (float)(dist / 6.0) * 0.4f;
                    microbiome.tryInfect(strain, -0.3f - distPenalty, immune, registry);
                    com.grace.micromayhem.client.SicknessParticles.sneezeTransmission(mob, level);
                }
            }
        });
    }

    private static void spawnAerosolParticles(ServerLevel level,
                                               net.minecraft.world.entity.LivingEntity mob) {
        // Only occasionally to avoid particle spam
        if (Math.random() > 0.1) return;
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.SNEEZE,
            mob.getX(), mob.getEyeY(), mob.getZ(),
            3, 0.3, 0.1, 0.3, 0.05);
    }

    // ---- CONTACT SPREAD ----

    private static void tickContactExposure(Player player, ServerLevel level,
                                             HostMicrobiome microbiome, PlayerImmuneSystem immune,
                                             MicrobeRegistry registry) {
        // Contact from nearby mobs using MobColony (2 block radius)
        level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
            player.getBoundingBox().inflate(2.0),
            e -> e != player && e.getPersistentData().contains("MicroMayhem_MobColony"))
        .forEach(mob -> {
            com.grace.micromayhem.microbe.MobColony colony =
                com.grace.micromayhem.microbe.MobColony.load(mob);
            for (com.grace.micromayhem.microbe.MobColony.StrainEntry entry : colony.entries()) {
                if (entry.stage == com.grace.micromayhem.microbe.MobColony.StrainEntry.InfectionStage.LATENT)
                    continue;
                MicrobeStrain strain = registry.getStrain(entry.strainId);
                if (strain == null) continue;
                if (strain.spreadVectors.contains(SpreadVector.CONTACT)) {
                    microbiome.tryInfect(strain, 0.0f, immune, registry);
                }
            }
        });

        // Also contact from other players
        level.getEntitiesOfClass(Player.class,
            player.getBoundingBox().inflate(2.0),
            p -> p != player)
        .forEach(other -> {
            HostMicrobiome otherBio = new HostMicrobiome();
            otherBio.loadFromTag(other.getPersistentData());
            for (BodySystem sys : BodySystem.values()) {
                SystemColony colony = otherBio.getSystem(sys);
                for (long id : colony.getAllStrainIds()) {
                    if (colony.getStage(id) == SystemColony.InfectionStage.ACTIVE ||
                        colony.getStage(id) == SystemColony.InfectionStage.SEVERE) {
                        MicrobeStrain strain = registry.getStrain(id);
                        if (strain != null && strain.spreadVectors.contains(SpreadVector.CONTACT)) {
                            microbiome.tryInfect(strain, 0.0f, immune, registry);
                        }
                    }
                }
            }
        });
    }

    // ---- FOOD CONTAMINATION (called from event) ----

    /**
     * Derive strains present on a raw food item.
     * Deterministic from world seed XOR item type hashcode XOR biome.
     * Returns empty list for cooked/processed foods.
     */
    public static List<MicrobeStrain> getFoodStrains(ItemStack food, ServerLevel level,
                                                      BlockPos pos, MicrobeRegistry registry) {
        List<MicrobeStrain> result = new ArrayList<>();
        if (!isRawFood(food)) return result;

        BiomePressure pressure = BiomePressure.forPosition(level, pos);
        long seed = level.getSeed()
            ^ food.getItem().hashCode()
            ^ pos.asLong()
            ^ pressure.ordinal();

        Random rng = new Random(seed);
        int count = 1 + rng.nextInt(2); // 1–2 strains per food item

        for (int i = 0; i < count; i++) {
            long strainSeed = seed ^ ((long)(i + 1) * 0x9E3779B97F4A7C15L);
            MicrobeStrain strain = registry.getStrainForSeed(strainSeed);
            // Only include food-vector or soil-vector strains on food
            if (strain.spreadVectors.contains(SpreadVector.FOOD) ||
                strain.spreadVectors.contains(SpreadVector.SOIL)) {
                result.add(strain);
            }
        }
        return result;
    }

    public static boolean isRawFood(ItemStack stack) {
        return stack.is(Items.BEEF)           || stack.is(Items.CHICKEN)      ||
               stack.is(Items.PORKCHOP)       || stack.is(Items.MUTTON)       ||
               stack.is(Items.RABBIT)         || stack.is(Items.COD)          ||
               stack.is(Items.SALMON)         || stack.is(Items.ROTTEN_FLESH) ||
               stack.is(Items.SPIDER_EYE)   || stack.is(Items.POISONOUS_POTATO) ||
               stack.is(Items.MUSHROOM_STEW)|| stack.is(Items.BROWN_MUSHROOM) ||
               stack.is(Items.RED_MUSHROOM) || stack.is(Items.BEEF)         ||
               stack.is(Items.PORKCHOP)     || // these are cooked but kept for completeness check
               false;
    }

    /** Returns true for cooked/safe foods that should NOT carry strains */
    public static boolean isCookedFood(ItemStack stack) {
        return stack.is(Items.COOKED_BEEF)    || stack.is(Items.COOKED_CHICKEN) ||
               stack.is(Items.COOKED_PORKCHOP)|| stack.is(Items.COOKED_MUTTON)  ||
               stack.is(Items.COOKED_RABBIT)  || stack.is(Items.COOKED_COD)     ||
               stack.is(Items.COOKED_SALMON)  || stack.is(Items.BREAD)          ||
               stack.is(Items.BAKED_POTATO);
    }

    // ---- WATER DRINKING (called from event) ----

    /**
     * Derive waterborne strains from drinking water in the current biome.
     */
    public static List<MicrobeStrain> getDrinkingWaterStrains(ServerLevel level,
                                                               BlockPos pos,
                                                               MicrobeRegistry registry) {
        List<MicrobeStrain> result = new ArrayList<>();
        BiomePressure pressure = BiomePressure.forPosition(level, pos);

        // Only certain biomes have contaminated water
        if (pressure == BiomePressure.NEUTRAL || pressure == BiomePressure.HEAT
                || pressure == BiomePressure.EXTREME_HEAT) {
            return result; // clean water in dry/neutral biomes
        }

        long seed = level.getSeed() ^ pos.asLong() ^ 0xD4176BAL;
        MicrobeStrain strain = registry.getStrainForSeed(seed);
        if (strain.spreadVectors.contains(SpreadVector.WATER)) {
            result.add(strain);
        }
        return result;
    }

    // ---- Helpers ----

    /**
     * When the player is wearing a full hazmat suit, strains that would have infected them
     * are instead absorbed into the suit pieces, gradually contaminating them.
     * Runs every 5 seconds, matching aerosol exposure cadence.
     */
    private static void absorbEnvironmentalStrains(Player player, ServerLevel level,
                                                    MicrobeRegistry registry) {
        // Called every second from ModEvents; run every 5 calls = every 5s
        if ((player.tickCount / 20) % 5 != 0) return;

        java.util.List<MicrobeStrain> nearby = new java.util.ArrayList<>();

        // Airborne/aerosol — collect from infected mobs within 8 blocks
        for (net.minecraft.world.entity.LivingEntity mob :
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(8))) {
            if (mob == player) continue;
            MobColony colony = MobColony.load(mob);
            for (MobColony.StrainEntry entry : colony.entries()) {
                if (entry.stage == MobColony.StrainEntry.InfectionStage.LATENT) continue;
                MicrobeStrain s = registry.getStrain(entry.strainId);
                if (s != null && !nearby.contains(s)) nearby.add(s);
            }
        }

        // Block contamination — shed strains coat the outside of the suit
        BlockContaminationData blockData = BlockContaminationData.get(level);
        BlockPos feet = player.blockPosition();
        for (long id : blockData.getStrains(feet)) {
            MicrobeStrain s = registry.getStrain(id);
            if (s != null && !nearby.contains(s)) nearby.add(s);
        }
        for (long id : blockData.getStrains(feet.below())) {
            MicrobeStrain s = registry.getStrain(id);
            if (s != null && !nearby.contains(s)) nearby.add(s);
        }

        if (nearby.isEmpty()) return;

        // Absorb one random strain into a random hazmat piece
        java.util.Random rng = new java.util.Random(level.getGameTime() ^ player.getUUID().getLeastSignificantBits());
        MicrobeStrain strain = nearby.get(rng.nextInt(nearby.size()));

        net.minecraft.world.entity.EquipmentSlot[] slots = {
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET
        };
        net.minecraft.world.item.ItemStack piece = player.getItemBySlot(slots[rng.nextInt(slots.length)]);
        if (piece.getItem() instanceof HazmatArmorItem) {
            HazmatArmorItem.absorbStrain(piece, strain.strainId);
        }
    }


    private static boolean isFullHazmat(Player player) {
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                   .getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                   .getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
                   .getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem
            && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
                   .getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem;
    }
}
