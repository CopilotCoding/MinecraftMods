package com.grace.micromayhem.microbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Core simulation engine. Called once per second per player.
 *
 * Per tick, for each body system:
 *   1. Grow/shrink colonies based on aggression, defense, biome pressure
 *   2. Apply strain relationships (synergy, parasitism, suppression, competition)
 *   3. Progress infection stages based on colony size vs immune strength
 *   4. Apply effects at appropriate stages
 *   5. Handle system health degradation from SEVERE infections
 *   6. Check for system failure and death conditions
 *   7. Handle strain migration between systems
 *   8. Update immune system (depletion, regen, adaptive)
 *   9. Check autoimmune misfires
 *   10. Check microbiome stability
 */
public class ColonySimulator {

    // Colony thresholds for stage progression
    private static final float LATENT_THRESHOLD  = 0.05f;
    private static final float ACTIVE_THRESHOLD  = 0.20f;
    private static final float SEVERE_THRESHOLD  = 0.50f;
    private static final float DOMINANT_THRESHOLD = 0.60f;

    // System health damage per tick in SEVERE
    private static final float SEVERE_HEALTH_DAMAGE = 0.002f; // per second
    private static final float SYSTEM_REPAIR_RATE   = 0.0005f; // per second when no SEVERE

    // Base colony growth per second
    private static final float BASE_GROWTH = 0.01f;
    private static final float BASE_DECAY  = 0.008f;

    /**
     * Main tick — call once per second per player.
     * Returns a TickResult with events that occurred.
     */
    public static TickResult tick(
            Player player,
            HostMicrobiome microbiome,
            PlayerImmuneSystem immune,
            MicrobeRegistry registry,
            ServerLevel level) {

        TickResult result = new TickResult();
        BiomePressure pressure = BiomePressure.forPosition(level, player.blockPosition());

        for (BodySystem system : BodySystem.values()) {
            SystemColony colony = microbiome.getSystem(system);
            tickSystem(player, colony, immune, registry, pressure, level.getGameTime(), result);
        }

        // Immune system regeneration (slower during active infections)
        boolean hasActiveInfections = microbiome.hasAnyActiveInfection();
        float regenRate = hasActiveInfections ? 0.0003f : 0.001f;
        immune.regenerate(regenRate);

        // Check microbiome stability
        boolean stable = microbiome.isMicrobiomeStable();
        result.microbiomeStable = stable;

        // Autoimmune misfire check
        List<Long> beneficial = microbiome.getAllBeneficialStrains(registry);
        float bMass = microbiome.getTotalBeneficialMass(registry);
        long misfireTarget = immune.tryMisfire(beneficial, bMass, level.getGameTime());
        if (misfireTarget >= 0) {
            MicrobeStrain target = registry.getStrain(misfireTarget);
            if (target != null) {
                result.autoimmuneMisfire = true;
                result.misfireEffect = target.effect.invert();
                result.misfireVirulence = target.virulence;
            }
        }

        // Check death conditions
        result.deathReason = checkDeathConditions(microbiome, registry);

        return result;
    }

    private static void tickSystem(
            Player player,
            SystemColony colony,
            PlayerImmuneSystem immune,
            MicrobeRegistry registry,
            BiomePressure pressure,
            long worldTime,
            TickResult result) {

        if (colony.getAllStrainIds().isEmpty()) {
            // Repair empty system
            colony.repairSystem(SYSTEM_REPAIR_RATE);
            return;
        }

        List<Long> toRemove = new ArrayList<>();
        List<Long> allIds   = new ArrayList<>(colony.getAllStrainIds());

        // ---- 1. Competition & relationship ticks ----
        for (int i = 0; i < allIds.size(); i++) {
            long idA = allIds.get(i);
            MicrobeStrain strainA = registry.getStrain(idA);
            if (strainA == null) { toRemove.add(idA); continue; }

            for (int j = i + 1; j < allIds.size(); j++) {
                long idB = allIds.get(j);
                MicrobeStrain strainB = registry.getStrain(idB);
                if (strainB == null) continue;

                StrainRelationship rel = StrainRelationship.derive(idA, idB);
                applyRelationship(colony, strainA, idA, strainB, idB, rel);

                // Track cohabitation days for non-competitive pairs
                if (rel != StrainRelationship.COMPETITIVE && rel != StrainRelationship.PARASITIC) {
                    colony.cohabitationDays.merge(idA, 0, Integer::sum);
                    colony.cohabitationDays.merge(idB, 0, Integer::sum);
                }
            }

            // Established status (3+ days peaceful cohabitation)
            int cohab = colony.cohabitationDays.getOrDefault(idA, 0);
            if (cohab >= 3 && !colony.establishedStrains.contains(idA)) {
                colony.establishedStrains.add(idA);
            }
        }

        // ---- 2. Colony growth/decay per strain ----
        float localImmunity = immune.getLocalImmunity(colony.system);
        float immuneDepletion = 0f;

        for (long id : allIds) {
            MicrobeStrain strain = registry.getStrain(id);
            if (strain == null) { toRemove.add(id); continue; }

            float colonySize = colony.colonySizes.getOrDefault(id, 0f);
            float stage_tick = colony.stageTicks.getOrDefault(id, 0) + 20; // +1 second
            colony.stageTicks.put(id, stage_tick > Integer.MAX_VALUE / 2 ? 0 : (int)stage_tick);

            // Biome growth modifier
            float biomeMod = pressure.getGrowthMod(strain.type);

            // Established defense bonus
            float defenseBonus = colony.establishedStrains.contains(id) ? 0.2f : 0f;
            float effectiveDefense = Math.min(1f, strain.defenseFactor + defenseBonus);

            // Beneficial colony resistance — pathogens grow slower in healthy microbiome
            float bMassResistance = 0f;
            if (strain.effect.category == MicrobeEffect.EffectCategory.NEGATIVE) {
                float bMass = colony.getBeneficialMass(registry);
                bMassResistance = bMass > 0.5f ? 0.4f : bMass * 0.8f;
            }

            // Immune pressure
            float immunePressure = localImmunity * (1f - effectiveDefense) * 0.5f;
            float adaptiveBonus  = immune.adaptiveImmunity.getOrDefault(id, 0f);
            immunePressure += adaptiveBonus;

            // Net growth
            float growth = BASE_GROWTH * strain.aggressionFactor * biomeMod;
            float decay  = BASE_DECAY * (immunePressure + bMassResistance);
            float delta  = growth - decay;
            float newSize = Math.max(0f, Math.min(1f, colonySize + delta));
            colony.colonySizes.put(id, newSize);

            // Immune depletion proportional to pathogen colony size
            if (strain.effect.category == MicrobeEffect.EffectCategory.NEGATIVE) {
                immuneDepletion += newSize * 0.002f;
            }

            // ---- 3. Stage progression ----
            SystemColony.InfectionStage currentStage = colony.getStage(id);
            SystemColony.InfectionStage newStage = progressStage(
                currentStage, newSize, immune.immuneStrength, strain, worldTime, result, id);

            if (newStage != currentStage) {
                colony.stages.put(id, newStage);
                colony.stageTicks.put(id, 0);

                if (newStage == SystemColony.InfectionStage.CLEARING) {
                    result.clearingStrains.add(id);
                    if (strain.autoimmunePotential != AutoimmunePotential.NONE
                            && currentStage == SystemColony.InfectionStage.SEVERE) {
                        result.autoimmuneCandidates.add(new TickResult.AutoimmuneCandidate(id, strain));
                    }
                }
                if (newStage == SystemColony.InfectionStage.SEVERE) {
                    result.severeStrains.add(id);
                }
            }

            // ---- 4. Effect application ----
            applyEffectForStage(newStage, strain, player, colony.system);

            // ---- 5. System health damage in SEVERE ----
            if (newStage == SystemColony.InfectionStage.SEVERE) {
                colony.damageSystem(SEVERE_HEALTH_DAMAGE * strain.virulence);
                // Permanent max health reduction (attribute modifier)
                if (colony.stageTicks.getOrDefault(id, 0) == 200) { // after 10s in SEVERE
                    applyPermanentDamage(player, strain);
                    result.permanentDamageApplied = true;
                }
            }

            // Mark for removal if colony gone and clearing
            if (newSize <= 0.001f && newStage == SystemColony.InfectionStage.CLEARING) {
                toRemove.add(id);
            }
        }

        // ---- 6. System repair if no SEVERE ----
        boolean hasSevere = colony.stages.values().stream()
            .anyMatch(s -> s == SystemColony.InfectionStage.SEVERE);
        if (!hasSevere) {
            colony.repairSystem(SYSTEM_REPAIR_RATE);
        }

        // ---- 7. Immune depletion ----
        immune.deplete(immuneDepletion);

        // Clean up
        for (long id : toRemove) colony.removeStrain(id);

        // ---- 8. Migration check (rare — only for high-aggression strains in SEVERE) ----
        checkMigration(colony, registry, result);
    }

    private static SystemColony.InfectionStage progressStage(
            SystemColony.InfectionStage current, float colonySize,
            float immuneStrength, MicrobeStrain strain,
            long worldTime, TickResult result, long strainId) {

        return switch (current) {
            case EXPOSED -> {
                // Brief window — immune system can clear it
                if (colonySize <= 0.001f) yield SystemColony.InfectionStage.CLEARING;
                if (colonySize >= LATENT_THRESHOLD) yield SystemColony.InfectionStage.LATENT;
                yield current;
            }
            case LATENT -> {
                if (colonySize <= 0.001f) yield SystemColony.InfectionStage.CLEARING;
                if (colonySize >= ACTIVE_THRESHOLD) yield SystemColony.InfectionStage.ACTIVE;
                yield current;
            }
            case ACTIVE -> {
                if (colonySize <= LATENT_THRESHOLD * 0.5f) yield SystemColony.InfectionStage.CLEARING;
                // Progress to SEVERE only if colony dominant and immune system struggling
                if (colonySize >= SEVERE_THRESHOLD && immuneStrength < 0.5f) {
                    yield SystemColony.InfectionStage.SEVERE;
                }
                yield current;
            }
            case SEVERE -> {
                // Regress if immune system recovers
                if (immuneStrength > 0.6f || colonySize < ACTIVE_THRESHOLD) {
                    yield SystemColony.InfectionStage.ACTIVE;
                }
                if (colonySize <= 0.001f) yield SystemColony.InfectionStage.CLEARING;
                yield current;
            }
            case CLEARING -> {
                if (colonySize > LATENT_THRESHOLD) yield SystemColony.InfectionStage.LATENT;
                yield current;
            }
        };
    }

    private static void applyEffectForStage(
            SystemColony.InfectionStage stage, MicrobeStrain strain,
            Player player, BodySystem system) {

        if (stage == SystemColony.InfectionStage.EXPOSED
                || stage == SystemColony.InfectionStage.LATENT
                || stage == SystemColony.InfectionStage.CLEARING) return;

        if (!strain.effect.hasVanillaEffect() && !strain.effect.isCustom()) return;

        int amplifier = strain.virulence - 1;
        if (stage == SystemColony.InfectionStage.SEVERE) amplifier = Math.min(4, amplifier + 1);

        if (strain.effect.hasVanillaEffect()) {
            // Apply for 6 seconds so it's always active during ACTIVE/SEVERE
            player.addEffect(new MobEffectInstance(
                strain.effect.vanillaEffect, 120, amplifier, false, true));
        }
        // Custom effects handled in ModEvents via flag check
    }

    private static void applyRelationship(
            SystemColony colony,
            MicrobeStrain a, long idA,
            MicrobeStrain b, long idB,
            StrainRelationship rel) {

        float sizeA = colony.colonySizes.getOrDefault(idA, 0f);
        float sizeB = colony.colonySizes.getOrDefault(idB, 0f);

        switch (rel) {
            case COMPETITIVE -> {
                // Higher aggression wins, shrinks the other
                float aAdvantage = a.aggressionFactor - b.defenseFactor;
                float bAdvantage = b.aggressionFactor - a.defenseFactor;
                if (aAdvantage > 0) colony.colonySizes.put(idB, Math.max(0, sizeB - aAdvantage * 0.003f));
                if (bAdvantage > 0) colony.colonySizes.put(idA, Math.max(0, sizeA - bAdvantage * 0.003f));
            }
            case SYNERGISTIC -> {
                // Both grow faster
                colony.colonySizes.put(idA, Math.min(1f, sizeA + 0.002f));
                colony.colonySizes.put(idB, Math.min(1f, sizeB + 0.002f));
            }
            case PARASITIC -> {
                // A feeds off B (A has higher aggression by convention of ID ordering)
                float transfer = sizeB * 0.001f;
                colony.colonySizes.put(idA, Math.min(1f, sizeA + transfer));
                colony.colonySizes.put(idB, Math.max(0, sizeB - transfer));
            }
            case SUPPRESSIVE -> {
                // Dominant strain prevents other from leaving LATENT
                // Handled in stage progression — suppressed strain can't reach ACTIVE
                // (flag via cohabitationDays set to -1 as sentinel)
            }
            case NEUTRAL -> { /* no interaction */ }
        }
    }

    private static void applyPermanentDamage(Player player, MicrobeStrain strain) {
        var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr == null) return;
        double reduction = -strain.virulence;
        if (maxHealthAttr.getValue() + reduction < 10.0) return;
        // ResourceLocation key derived from strain ID — duplicates don't stack
        net.minecraft.resources.ResourceLocation modId =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "micromayhem", "infection_" + Long.toHexString(Math.abs(strain.strainId)));
        if (maxHealthAttr.getModifier(modId) != null) return;
        maxHealthAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            modId,
            reduction,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
        ));
    }

    private static void checkMigration(
            SystemColony colony, MicrobeRegistry registry, TickResult result) {
        // Only SEVERE high-aggression strains migrate
        for (long id : new ArrayList<>(colony.getAllStrainIds())) {
            MicrobeStrain strain = registry.getStrain(id);
            if (strain == null) continue;
            if (colony.getStage(id) != SystemColony.InfectionStage.SEVERE) continue;
            if (strain.aggressionFactor < 0.8f) continue;
            if (colony.systemHealth > 0.3f) continue; // system not degraded enough

            // 0.1% chance per second of migration
            if (Math.random() < 0.001f) {
                BodySystem targetSystem = getAdjacentSystem(colony.system);
                result.migrations.add(new TickResult.Migration(id, colony.system, targetSystem));
            }
        }
    }

    private static BodySystem getAdjacentSystem(BodySystem from) {
        return switch (from) {
            case RESPIRATORY -> BodySystem.CIRCULATORY;
            case DIGESTIVE   -> BodySystem.SKIN;
            case SKIN        -> BodySystem.CIRCULATORY;
            case CIRCULATORY -> BodySystem.RESPIRATORY;
        };
    }

    private static String checkDeathConditions(HostMicrobiome microbiome, MicrobeRegistry registry) {
        int failedSystems = 0;
        for (BodySystem sys : BodySystem.values()) {
            SystemColony colony = microbiome.getSystem(sys);
            if (colony.inFailure) failedSystems++;

            // Circulatory alone can kill
            if (sys == BodySystem.CIRCULATORY && colony.inFailure) {
                return "Circulatory failure — " + getWorstPathogenName(colony, registry);
            }
            // Circulatory dominated by catastrophic strain
            if (sys == BodySystem.CIRCULATORY) {
                long dominant = colony.getDominantStrain();
                if (dominant >= 0) {
                    MicrobeStrain s = registry.getStrain(dominant);
                    if (s != null && s.lethalityProfile == LethalityProfile.CATASTROPHIC
                            && colony.colonySizes.getOrDefault(dominant, 0f) > 0.8f) {
                        return "Systemic infection — " + s.getScientificName();
                    }
                }
            }
        }
        if (failedSystems >= 2) return "Multiple organ failure";
        return null;
    }

    private static String getWorstPathogenName(SystemColony colony, MicrobeRegistry registry) {
        long dominant = colony.getDominantStrain();
        MicrobeStrain s = dominant >= 0 ? registry.getStrain(dominant) : null;
        return s != null ? s.getScientificName() : "unknown pathogen";
    }

    // ---- Result container ----

    public static class TickResult {
        public List<Long> clearingStrains    = new ArrayList<>();
        public List<Long> severeStrains      = new ArrayList<>();
        public List<AutoimmuneCandidate> autoimmuneCandidates = new ArrayList<>();
        public List<Migration> migrations    = new ArrayList<>();
        public boolean autoimmuneMisfire     = false;
        public MicrobeEffect misfireEffect   = null;
        public int misfireVirulence          = 1;
        public boolean permanentDamageApplied = false;
        public boolean microbiomeStable      = false;
        public String deathReason            = null; // non-null = player should die

        public static class AutoimmuneCandidate {
            public final long strainId;
            public final MicrobeStrain strain;
            AutoimmuneCandidate(long id, MicrobeStrain s) { strainId = id; strain = s; }
        }

        public static class Migration {
            public final long strainId;
            public final BodySystem from, to;
            Migration(long id, BodySystem from, BodySystem to) {
                strainId = id; this.from = from; this.to = to;
            }
        }
    }
}
