package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Attached to each player (via capability-like NBT on PlayerLoggedIn).
 * Tracks:
 *  - Active infections (strain IDs + tick countdown)
 *  - Immune strains (vaccinated or naturally cleared)
 *  - Body flora (stable background strains)
 *  - Infection stage (EXPOSED → LATENT → ACTIVE → CLEARING)
 *
 * Updated server-side via PlayerTickEvent (once per second, not per tick).
 */
public class PlayerMicrobiome {

    public static final String NBT_KEY = "MicroMayhem_Microbiome";

    public enum InfectionStage { LATENT, ACTIVE, CLEARING }

    public static class ActiveInfection {
        public long strainId;
        public InfectionStage stage;
        public int ticksRemaining;
        public int latentTicksRemaining; // delay before ACTIVE symptoms

        public ActiveInfection(long strainId, int latentTicks, int activeTicks) {
            this.strainId = strainId;
            this.stage = InfectionStage.LATENT;
            this.latentTicksRemaining = latentTicks;
            this.ticksRemaining = activeTicks;
        }

        public ActiveInfection() {}
    }

    private final Map<Long, ActiveInfection> infections = new LinkedHashMap<>();
    private final Set<Long> immuneStrains = new HashSet<>();
    private final List<Long> bodyFlora = new ArrayList<>(); // background; no active effect

    // ---- Infection ----

    /** Attempt to infect the player with a strain. Returns true if new infection started. */
    public boolean tryInfect(MicrobeStrain strain, float transmissibilityBonus) {
        long id = strain.strainId;
        if (immuneStrains.contains(id)) return false;
        if (infections.containsKey(id)) return false;

        float chance = strain.transmissibility + transmissibilityBonus;
        if (Math.random() > chance) return false;

        int latent = 200 + (int)(Math.random() * 400); // 10–30s latent period
        int active = strain.effectDurationTicks;
        infections.put(id, new ActiveInfection(id, latent, active));
        return true;
    }

    /** Grant immunity to a strain (vaccine or natural). */
    public void grantImmunity(long strainId) {
        immuneStrains.add(strainId);
        infections.remove(strainId);
    }

    public boolean isImmune(long strainId) {
        return immuneStrains.contains(strainId);
    }

    public int getLatentTicks(long strainId) {
        ActiveInfection inf = infections.get(strainId);
        return inf != null ? inf.latentTicksRemaining : 0;
    }

    public boolean isInfected(long strainId) {
        return infections.containsKey(strainId);
    }

    public Collection<ActiveInfection> getActiveInfections() {
        return Collections.unmodifiableCollection(infections.values());
    }

    public Set<Long> getImmuneStrains() {
        return Collections.unmodifiableSet(immuneStrains);
    }

    public List<Long> getBodyFlora() {
        return Collections.unmodifiableList(bodyFlora);
    }

    /**
     * Called once per second. Advances infection stages, applies effects.
     * Returns true if state changed (so caller can sync to client if needed).
     */
    public boolean tick(Player player, MicrobeRegistry registry) {
        boolean changed = false;
        List<Long> toRemove = new ArrayList<>();

        // Iterate over a snapshot to avoid ConcurrentModificationException
        List<Map.Entry<Long, ActiveInfection>> snapshot =
            new ArrayList<>(infections.entrySet());

        for (Map.Entry<Long, ActiveInfection> entry : snapshot) {
            ActiveInfection inf = entry.getValue();
            MicrobeStrain strain = registry.getStrain(inf.strainId);
            if (strain == null) { toRemove.add(entry.getKey()); continue; }

            switch (inf.stage) {
                case LATENT -> {
                    inf.latentTicksRemaining -= 20;
                    if (inf.latentTicksRemaining <= 0) {
                        inf.stage = InfectionStage.ACTIVE;
                        changed = true;
                    }
                }
                case ACTIVE -> {
                    if (inf.ticksRemaining % 100 == 0) {
                        applyEffect(player, strain);
                    }
                    inf.ticksRemaining -= 20;
                    if (inf.ticksRemaining <= 0) {
                        inf.stage = InfectionStage.CLEARING;
                        changed = true;
                    }
                }
                case CLEARING -> {
                    toRemove.add(entry.getKey());
                    grantImmunity(inf.strainId);
                    changed = true;
                }
            }
        }

        for (Long id : toRemove) infections.remove(id);
        return changed;
    }

    private void applyEffect(Player player, MicrobeStrain strain) {
        if (!strain.effect.hasVanillaEffect()) return;
        int amplifier = strain.virulence - 1; // 0, 1, or 2
        int duration = 100 + amplifier * 40;  // 5–7 seconds between reapplications
        player.addEffect(new MobEffectInstance(strain.effect.vanillaEffect, duration, amplifier, false, true));
    }

    /** Cure all infections from a given antibiotic type. */
    public void cureAllBacterial(MicrobeRegistry registry) {
        infections.entrySet().removeIf(e -> {
            MicrobeStrain s = registry.getStrain(e.getKey());
            return s != null && s.type == MicrobeType.BACTERIA;
        });
    }

    public void cureAllViral(MicrobeRegistry registry) {
        infections.entrySet().removeIf(e -> {
            MicrobeStrain s = registry.getStrain(e.getKey());
            return s != null && s.type == MicrobeType.VIRUS;
        });
    }

    public void cureAllFungal(MicrobeRegistry registry) {
        infections.entrySet().removeIf(e -> {
            MicrobeStrain s = registry.getStrain(e.getKey());
            return s != null && s.type == MicrobeType.FUNGUS;
        });
    }

    public void cureAllParasitic(MicrobeRegistry registry) {
        infections.entrySet().removeIf(e -> {
            MicrobeStrain s = registry.getStrain(e.getKey());
            return s != null && s.type == MicrobeType.PARASITE;
        });
    }

    // ---- NBT ----

    public void saveToTag(CompoundTag playerTag) {
        CompoundTag tag = new CompoundTag();

        ListTag infList = new ListTag();
        for (ActiveInfection inf : infections.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("StrainId", inf.strainId);
            t.putString("Stage", inf.stage.name());
            t.putInt("TicksRemaining", inf.ticksRemaining);
            t.putInt("LatentTicks", inf.latentTicksRemaining);
            infList.add(t);
        }
        tag.put("Infections", infList);

        long[] immuneArr = immuneStrains.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("ImmuneStrains", immuneArr);

        long[] floraArr = bodyFlora.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("BodyFlora", floraArr);

        playerTag.put(NBT_KEY, tag);
    }

    public void loadFromTag(CompoundTag playerTag) {
        if (!playerTag.contains(NBT_KEY)) return;
        CompoundTag tag = playerTag.getCompoundOrEmpty(NBT_KEY);

        infections.clear();
        ListTag infList = tag.getListOrEmpty("Infections");
        for (int i = 0; i < infList.size(); i++) {
            CompoundTag t = infList.getCompoundOrEmpty(i);
            ActiveInfection inf = new ActiveInfection();
            inf.strainId = t.getLongOr("StrainId", 0L);
            inf.stage = InfectionStage.valueOf(t.getStringOr("Stage", ""));
            inf.ticksRemaining = t.getIntOr("TicksRemaining", 0);
            inf.latentTicksRemaining = t.getIntOr("LatentTicks", 0);
            infections.put(inf.strainId, inf);
        }

        immuneStrains.clear();
        for (long id : tag.getLongArray("ImmuneStrains").orElse(new long[0])) immuneStrains.add(id);

        bodyFlora.clear();
        for (long id : tag.getLongArray("BodyFlora").orElse(new long[0])) bodyFlora.add(id);
    }
}
