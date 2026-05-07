package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * A single procedurally generated microorganism strain.
 * v2.0 — expanded with aggression/defense, lethality, autoimmune potential,
 * body system affinity, biome preference, and relationship seed.
 */
public class MicrobeStrain {

    private static final String[] GENUS_PREFIXES = {
        "Crypto","Staphy","Strepto","Bacil","Myco","Pseudo","Escheri","Vibrio",
        "Clostri","Listeria","Salmo","Campy","Helico","Entero","Neisseria",
        "Shigel","Bordetella","Legionel","Klebsiel","Acineto","Proteo","Serratia",
        "Gloom","Vex","Pesti","Nox","Umbra","Caern","Mal","Sinis"
    };
    private static final String[] GENUS_SUFFIXES = {
        "coccus","bacillus","ella","bacter","monas","spirum","phage","vora",
        "lytica","toxica","ferens","cola","philum","forma","oides","plasma"
    };
    private static final String[] SPECIES_WORDS = {
        "necrotis","viridis","obscura","rapida","lenta","mortalis","benigna",
        "profunda","cavernae","silvae","aquatica","terrae","aeris","frigida",
        "calida","radians","nocturna","diurna","magna","parva","acuta","crassa",
        "flava","rubra","nigra","alba","aurea","grisea","caerulea","viridula"
    };

    // ---- Core identity ----
    public long strainId;
    public String genus;
    public String species;
    public MicrobeType type;
    public MicrobeEffect effect;
    public List<SpreadVector> spreadVectors;
    public int virulence;
    public float transmissibility;
    public int generation;
    public float mutationAccumulator;
    public int effectDurationTicks;

    // ---- v2.0 fields ----
    public float aggressionFactor;   // 0–1: how fast this strain outcompetes others
    public float defenseFactor;      // 0–1: how resistant to being outcompeted
    public AutoimmunePotential autoimmunePotential;
    public LethalityProfile lethalityProfile;
    public BodySystem primarySystem; // which body system it targets first
    public BiomePressure biomePreference; // biome it thrives in
    public long relationshipSeed;    // used to derive relationships with other strains
    public boolean fullyCharacterised; // true after 3 microscope views (world strain mechanic)
    public int microscopeViewCount;
    public boolean isWorldStrain;

    // ---- Population tracking ----
    public int hostCount;            // how many current hosts carry this strain
    public int daysSinceLastHost;    // for dormancy

    public MicrobeStrain() {
        spreadVectors = new ArrayList<>();
    }

    // ---- Generation ----

    public static MicrobeStrain generate(long seed) {
        Random rng = new Random(seed);
        MicrobeStrain s = new MicrobeStrain();
        s.strainId = seed;

        s.genus   = capitalize(GENUS_PREFIXES[rng.nextInt(GENUS_PREFIXES.length)]
                             + GENUS_SUFFIXES[rng.nextInt(GENUS_SUFFIXES.length)]);
        s.species = SPECIES_WORDS[rng.nextInt(SPECIES_WORDS.length)];

        int typeRoll = rng.nextInt(10);
        if (typeRoll < 4)      s.type = MicrobeType.BACTERIA;
        else if (typeRoll < 6) s.type = MicrobeType.VIRUS;
        else if (typeRoll < 8) s.type = MicrobeType.FUNGUS;
        else                   s.type = MicrobeType.PARASITE;

        // Effect
        int effectRoll = rng.nextInt(10);
        MicrobeEffect[] effects = MicrobeEffect.values();
        if (effectRoll < 3) {
            s.effect = MicrobeEffect.NONE;
        } else if (effectRoll < 7) {
            MicrobeEffect[] harmful = Arrays.stream(effects)
                .filter(e -> e.category == MicrobeEffect.EffectCategory.NEGATIVE)
                .toArray(MicrobeEffect[]::new);
            s.effect = harmful[rng.nextInt(harmful.length)];
        } else {
            MicrobeEffect[] positive = Arrays.stream(effects)
                .filter(e -> e.category == MicrobeEffect.EffectCategory.POSITIVE)
                .toArray(MicrobeEffect[]::new);
            s.effect = positive[rng.nextInt(positive.length)];
        }

        // Vectors
        SpreadVector[] allVectors = SpreadVector.values();
        int vectorCount = 1 + rng.nextInt(3);
        List<SpreadVector> shuffled = new ArrayList<>(List.of(allVectors));
        Collections.shuffle(shuffled, rng);
        s.spreadVectors = new ArrayList<>(shuffled.subList(0, vectorCount));

        s.virulence         = 1 + rng.nextInt(3);
        s.transmissibility  = 0.05f + rng.nextFloat() * 0.6f;
        s.generation        = 0;
        s.mutationAccumulator = 0f;
        s.effectDurationTicks = (600 + rng.nextInt(2400)) * s.virulence;

        // v2.0 fields
        s.aggressionFactor       = rng.nextFloat();
        s.defenseFactor          = rng.nextFloat();
        // Beneficial strains bias toward defense over aggression
        if (s.effect.category == MicrobeEffect.EffectCategory.POSITIVE) {
            s.defenseFactor    = Math.min(1.0f, s.defenseFactor + 0.2f);
            s.aggressionFactor = Math.max(0.0f, s.aggressionFactor - 0.1f);
        }
        // High virulence correlates with high aggression
        s.aggressionFactor = Math.min(1.0f, s.aggressionFactor + (s.virulence - 1) * 0.15f);

        s.autoimmunePotential = AutoimmunePotential.fromSeed(seed);
        s.lethalityProfile    = LethalityProfile.fromSeed(seed ^ 0xDEADC0DEL);
        s.primarySystem       = BodySystem.primaryAffinity(s);
        s.biomePreference     = biomeFromSeed(seed, rng);
        s.relationshipSeed    = rng.nextLong();
        s.fullyCharacterised  = false;
        s.microscopeViewCount = 0;
        s.isWorldStrain       = false;
        s.hostCount           = 0;
        s.daysSinceLastHost   = 0;

        return s;
    }

    public MicrobeStrain mutate(long newSeed) {
        Random rng = new Random(newSeed);
        MicrobeStrain child = new MicrobeStrain();
        child.strainId   = newSeed;
        child.genus      = this.genus;
        child.species    = rng.nextFloat() < 0.4f
            ? SPECIES_WORDS[rng.nextInt(SPECIES_WORDS.length)]
            : this.species + "-" + (this.generation + 1);
        child.type       = this.type;
        child.generation = this.generation + 1;
        child.mutationAccumulator = 0f;

        child.effect = rng.nextFloat() < 0.25f
            ? MicrobeEffect.values()[rng.nextInt(MicrobeEffect.values().length)]
            : this.effect;

        child.spreadVectors = new ArrayList<>(this.spreadVectors);
        if (rng.nextFloat() < 0.3f && child.spreadVectors.size() < 4) {
            SpreadVector candidate = SpreadVector.values()[rng.nextInt(SpreadVector.values().length)];
            if (!child.spreadVectors.contains(candidate)) child.spreadVectors.add(candidate);
        }
        if (rng.nextFloat() < 0.15f && child.spreadVectors.size() > 1)
            child.spreadVectors.remove(rng.nextInt(child.spreadVectors.size()));

        child.virulence        = Math.max(1, Math.min(3, this.virulence + rng.nextInt(3) - 1));
        child.transmissibility = Math.max(0.02f, Math.min(0.95f,
            this.transmissibility + (rng.nextFloat() - 0.5f) * 0.2f));
        child.effectDurationTicks = Math.max(200,
            (int)(this.effectDurationTicks * (0.8f + rng.nextFloat() * 0.4f)));

        // v2.0 — drift aggression and defense
        child.aggressionFactor = Math.max(0f, Math.min(1f,
            this.aggressionFactor + (rng.nextFloat() - 0.5f) * 0.15f));
        child.defenseFactor    = Math.max(0f, Math.min(1f,
            this.defenseFactor + (rng.nextFloat() - 0.5f) * 0.15f));

        child.autoimmunePotential = AutoimmunePotential.fromSeed(newSeed);
        // Lethality can escalate through mutation (rare)
        child.lethalityProfile = mutateLethality(this.lethalityProfile, rng);
        child.primarySystem    = BodySystem.primaryAffinity(child);
        child.biomePreference  = this.biomePreference; // biome pref stable through mutation
        child.relationshipSeed = rng.nextLong();
        child.fullyCharacterised = false;
        child.microscopeViewCount = 0;
        child.isWorldStrain    = false;
        child.hostCount        = 0;
        child.daysSinceLastHost = 0;

        return child;
    }

    private static LethalityProfile mutateLethality(LethalityProfile parent, Random rng) {
        // 95% chance lethality stays same tier, 4% chance escalates, 1% chance drops
        float roll = rng.nextFloat();
        if (roll < 0.95f) return parent;
        if (roll < 0.99f) {
            return switch (parent) {
                case NON_LETHAL  -> LethalityProfile.CONDITIONAL;
                case CONDITIONAL -> LethalityProfile.ACUTE;
                case ACUTE       -> LethalityProfile.CATASTROPHIC;
                default          -> parent;
            };
        }
        return switch (parent) {
            case CATASTROPHIC -> LethalityProfile.ACUTE;
            case ACUTE        -> LethalityProfile.CONDITIONAL;
            default           -> parent;
        };
    }

    private static BiomePressure biomeFromSeed(long seed, Random rng) {
        BiomePressure[] pressures = BiomePressure.values();
        return pressures[rng.nextInt(pressures.length)];
    }

    // ---- Microscope view tracking ----
    public void recordMicroscopeView() {
        if (!fullyCharacterised) {
            microscopeViewCount++;
            if (microscopeViewCount >= 3) fullyCharacterised = true;
        }
    }

    // ---- Display ----

    public String getScientificName() { return genus + " " + species; }

    public String getVirulenceLabel() {
        return switch (virulence) {
            case 1  -> "Grade I (Mild)";
            case 2  -> "Grade II (Moderate)";
            case 3  -> "Grade III (Severe)";
            default -> "Unknown";
        };
    }

    public String getTransmissibilityLabel() {
        if (transmissibility < 0.2f) return "Very Low";
        if (transmissibility < 0.4f) return "Low";
        if (transmissibility < 0.6f) return "Moderate";
        if (transmissibility < 0.8f) return "High";
        return "Extreme";
    }

    public List<String> getMicroscopeReadout() {
        List<String> lines = new ArrayList<>();

        // World strain hides full info until characterised
        if (isWorldStrain && !fullyCharacterised) {
            lines.add("§4§lUNKNOWN ORGANISM");
            lines.add("§7Type: §c[UNCHARACTERISED] (View " + microscopeViewCount + "/3)");
            lines.add("§7Effect: §c[UNKNOWN]");
            lines.add("§7Further analysis required.");
            return lines;
        }

        lines.add("§6§l" + getScientificName());
        lines.add("§7Type: §f" + type.displayName + " (Gen. " + generation + ")");
        lines.add("§7Effect: §f" + effect.displayName);
        lines.add("§7Virulence: §f" + getVirulenceLabel());
        lines.add("§7Transmissibility: §f" + getTransmissibilityLabel());
        lines.add("§7System: §f" + primarySystem.displayName);

        StringBuilder vecs = new StringBuilder("§7Vectors: §f");
        for (int i = 0; i < spreadVectors.size(); i++) {
            vecs.append(spreadVectors.get(i).displayName);
            if (i < spreadVectors.size() - 1) vecs.append(", ");
        }
        lines.add(vecs.toString());

        lines.add("§7Aggression: §f" + aggressionLabel() +
                  " §7| Defense: §f" + defenseLabel());
        lines.add("§7Mutation Rate: §f" + String.format("%.1f%%",
            type.baseMutationRate * 100 * virulence));
        lines.add("§7Biome Preference: §f" + biomePreference.displayName);

        // Lethality — world strain hidden until fully characterised, others shown after 3 views
        if (isWorldStrain && !fullyCharacterised) {
            lines.add("§7Lethality: §c[Requires further analysis — " + microscopeViewCount + "/3 views]");
        } else {
            lines.add("§7Lethality: §f" + lethalityProfile.displayName);
        }

        if (autoimmunePotential != AutoimmunePotential.NONE && fullyCharacterised) {
            lines.add("§c⚠ Autoimmune potential: " + autoimmunePotential.name());
        }

        String cat = switch (effect.category) {
            case POSITIVE -> "§a[BENEFICIAL]";
            case NEGATIVE -> "§c[PATHOGENIC]";
            case NEUTRAL  -> "§e[COMMENSAL]";
        };
        if (effect == MicrobeEffect.NONE) cat = "§e[COMMENSAL — no effect]";
        lines.add(cat);

        return lines;
    }

    private String aggressionLabel() {
        if (aggressionFactor < 0.25f) return "Passive";
        if (aggressionFactor < 0.5f)  return "Low";
        if (aggressionFactor < 0.75f) return "Moderate";
        return "High";
    }

    private String defenseLabel() {
        if (defenseFactor < 0.25f) return "Vulnerable";
        if (defenseFactor < 0.5f)  return "Low";
        if (defenseFactor < 0.75f) return "Moderate";
        return "Entrenched";
    }

    // ---- NBT ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("StrainId", strainId);
        tag.putString("Genus", genus);
        tag.putString("Species", species);
        tag.putString("Type", type.name());
        tag.putString("Effect", effect.name());
        ListTag vecList = new ListTag();
        for (SpreadVector v : spreadVectors) vecList.add(StringTag.valueOf(v.name()));
        tag.put("Vectors", vecList);
        tag.putInt("Virulence", virulence);
        tag.putFloat("Transmissibility", transmissibility);
        tag.putInt("Generation", generation);
        tag.putFloat("MutationAccumulator", mutationAccumulator);
        tag.putInt("EffectDuration", effectDurationTicks);
        // v2.0 — null-safe for strains loaded from pre-v2 worlds
        tag.putFloat("Aggression", aggressionFactor);
        tag.putFloat("Defense", defenseFactor);
        tag.putString("AutoimmunePotential",
            autoimmunePotential != null ? autoimmunePotential.name() : AutoimmunePotential.NONE.name());
        tag.putString("LethalityProfile",
            lethalityProfile != null ? lethalityProfile.name() : LethalityProfile.NON_LETHAL.name());
        tag.putString("PrimarySystem",
            primarySystem != null ? primarySystem.name() : BodySystem.DIGESTIVE.name());
        tag.putString("BiomePreference",
            biomePreference != null ? biomePreference.name() : BiomePressure.NEUTRAL.name());
        tag.putLong("RelationshipSeed", relationshipSeed);
        tag.putBoolean("FullyCharacterised", fullyCharacterised);
        tag.putInt("MicroscopeViews", microscopeViewCount);
        tag.putBoolean("IsWorldStrain", isWorldStrain);
        tag.putInt("HostCount", hostCount);
        tag.putInt("DaysSinceLastHost", daysSinceLastHost);
        return tag;
    }

    public static MicrobeStrain load(CompoundTag tag) {
        MicrobeStrain s = new MicrobeStrain();
        s.strainId   = tag.getLong("StrainId");
        s.genus      = tag.getString("Genus");
        s.species    = tag.getString("Species");
        s.type       = MicrobeType.valueOf(tag.getString("Type"));
        s.effect     = MicrobeEffect.valueOf(tag.getString("Effect"));
        ListTag vecs = tag.getList("Vectors", Tag.TAG_STRING);
        for (int i = 0; i < vecs.size(); i++)
            s.spreadVectors.add(SpreadVector.valueOf(vecs.getString(i)));
        s.virulence           = tag.getInt("Virulence");
        s.transmissibility    = tag.getFloat("Transmissibility");
        s.generation          = tag.getInt("Generation");
        s.mutationAccumulator = tag.getFloat("MutationAccumulator");
        s.effectDurationTicks = tag.getInt("EffectDuration");
        // v2.0
        s.aggressionFactor    = tag.getFloat("Aggression");
        s.defenseFactor       = tag.getFloat("Defense");
        s.autoimmunePotential = tag.contains("AutoimmunePotential")
            ? AutoimmunePotential.valueOf(tag.getString("AutoimmunePotential"))
            : AutoimmunePotential.NONE;
        s.lethalityProfile    = tag.contains("LethalityProfile")
            ? LethalityProfile.valueOf(tag.getString("LethalityProfile"))
            : LethalityProfile.NON_LETHAL;
        s.primarySystem       = tag.contains("PrimarySystem")
            ? BodySystem.valueOf(tag.getString("PrimarySystem"))
            : BodySystem.DIGESTIVE;
        s.biomePreference     = tag.contains("BiomePreference")
            ? BiomePressure.valueOf(tag.getString("BiomePreference"))
            : BiomePressure.NEUTRAL;
        s.relationshipSeed    = tag.getLong("RelationshipSeed");
        s.fullyCharacterised  = tag.getBoolean("FullyCharacterised");
        s.microscopeViewCount = tag.getInt("MicroscopeViews");
        s.isWorldStrain       = tag.getBoolean("IsWorldStrain");
        s.hostCount           = tag.getInt("HostCount");
        s.daysSinceLastHost   = tag.getInt("DaysSinceLastHost");
        return s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
