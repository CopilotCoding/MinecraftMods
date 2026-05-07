package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.*;

/**
 * Lightweight infection model for non-player mobs.
 * Stored in mob persistent NBT under "MicroMayhem_MobColony".
 *
 * Unlike the player's four-system HostMicrobiome, mobs have a flat colony map.
 * Each strain progresses through Latent → Active → Severe independently.
 * Lethal strains kill the mob at Severe after a grace period.
 */
public class MobColony {

    private static final String NBT_KEY = "MicroMayhem_MobColony";
    private static final int LATENT_TICKS  = 1200;  // 60s
    private static final int ACTIVE_TICKS  = 2400;  // 120s
    private static final int SEVERE_TICKS  = 1200;  // 60s before lethal kills
    private static final float BASE_TRANSMISSION = 0.25f;

    public static class StrainEntry {
        public long strainId;
        public int ticksInfected;
        public InfectionStage stage;

        public StrainEntry(long strainId) {
            this.strainId = strainId;
            this.ticksInfected = 0;
            this.stage = InfectionStage.LATENT;
        }

        public enum InfectionStage { LATENT, ACTIVE, SEVERE }
    }

    private final Map<Long, StrainEntry> colonies = new LinkedHashMap<>();

    public boolean isEmpty() { return colonies.isEmpty(); }
    public Collection<StrainEntry> entries() { return colonies.values(); }
    public Set<Long> strainIds() { return colonies.keySet(); }

    public boolean tryInfect(MicrobeStrain strain) {
        if (colonies.containsKey(strain.strainId)) return false;
        if (colonies.size() >= 12) return false; // mob colony cap
        colonies.put(strain.strainId, new StrainEntry(strain.strainId));
        return true;
    }

    public boolean hasStrain(long id) { return colonies.containsKey(id); }

    /**
     * Tick the colony — called once per second from EntityTick event.
     * Returns true if the mob should die from infection.
     */
    public boolean tick(LivingEntity mob, MicrobeRegistry registry) {
        boolean shouldDie = false;
        List<Long> toRemove = new ArrayList<>();

        for (StrainEntry entry : colonies.values()) {
            entry.ticksInfected += 20; // called every 20 ticks (1s)
            MicrobeStrain strain = registry.getStrain(entry.strainId);
            if (strain == null) { toRemove.add(entry.strainId); continue; }

            // Stage progression
            if (entry.stage == StrainEntry.InfectionStage.LATENT
                    && entry.ticksInfected >= LATENT_TICKS) {
                entry.stage = StrainEntry.InfectionStage.ACTIVE;
                applyActiveSymptoms(mob, strain);
            } else if (entry.stage == StrainEntry.InfectionStage.ACTIVE
                    && entry.ticksInfected >= LATENT_TICKS + ACTIVE_TICKS) {
                entry.stage = StrainEntry.InfectionStage.SEVERE;
                applyActiveSymptoms(mob, strain);
            } else if (entry.stage == StrainEntry.InfectionStage.SEVERE
                    && entry.ticksInfected >= LATENT_TICKS + ACTIVE_TICKS + SEVERE_TICKS) {
                // Check lethality
                if (strain.lethalityProfile == LethalityProfile.NON_LETHAL) {
                    toRemove.add(entry.strainId); // clears naturally
                } else if (strain.lethalityProfile == LethalityProfile.ACUTE
                        || strain.lethalityProfile == LethalityProfile.CATASTROPHIC) {
                    shouldDie = true;
                } else {
                    // CONDITIONALLY_LETHAL — 30% chance of death, else clears
                    if (mob.getRandom().nextFloat() < 0.30f) shouldDie = true;
                    else toRemove.add(entry.strainId);
                }
            }

            // Apply periodic symptoms at Active/Severe
            if (entry.stage != StrainEntry.InfectionStage.LATENT
                    && entry.ticksInfected % 200 == 0) {
                applyActiveSymptoms(mob, strain);
            }
        }

        for (long id : toRemove) colonies.remove(id);
        return shouldDie;
    }

    private void applyActiveSymptoms(LivingEntity mob, MicrobeStrain strain) {
        int amp = strain.virulence >= 3 ? 1 : 0;
        switch (strain.effect) {
            case SLOWNESS          -> mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, amp, false, true));
            case WEAKNESS          -> mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, amp, false, true));
            case POISON            -> mob.addEffect(new MobEffectInstance(MobEffects.POISON, 100, amp, false, true));
            case WITHER            -> mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
            case NAUSEA            -> mob.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, true));
            case BLINDNESS         -> mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
            case REGENERATION      -> mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, amp, false, true));
            case SPEED             -> mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, amp, false, true));
            case RESISTANCE        -> mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, amp, false, true));
            case GLOWING           -> mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, true));
            default -> {} // NONE and custom effects — no vanilla equivalent for mobs
        }
    }

    // ---- NBT ----

    public static MobColony load(LivingEntity mob) {
        MobColony colony = new MobColony();
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(NBT_KEY)) return colony;
        CompoundTag tag = persistent.getCompound(NBT_KEY);
        ListTag list = tag.getList("Strains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long id = entry.getLong("Id");
            StrainEntry se = new StrainEntry(id);
            se.ticksInfected = entry.getInt("Ticks");
            se.stage = StrainEntry.InfectionStage.valueOf(
                entry.getString("Stage").isEmpty() ? "LATENT" : entry.getString("Stage"));
            colony.colonies.put(id, se);
        }
        return colony;
    }

    public void save(LivingEntity mob) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StrainEntry entry : colonies.values()) {
            CompoundTag e = new CompoundTag();
            e.putLong("Id", entry.strainId);
            e.putInt("Ticks", entry.ticksInfected);
            e.putString("Stage", entry.stage.name());
            list.add(e);
        }
        tag.put("Strains", list);
        mob.getPersistentData().put(NBT_KEY, tag);
    }

    public static float transmissionChance(MicrobeStrain strain) {
        return BASE_TRANSMISSION * (strain.transmissibility / 100f);
    }
}
