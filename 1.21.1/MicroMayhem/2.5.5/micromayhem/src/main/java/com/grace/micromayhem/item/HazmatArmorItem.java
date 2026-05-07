package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.MicrobeStrain;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Hazmat Suit armor piece.
 *
 * When worn (full set): blocks ALL environmental/contact/aerosol transmission.
 * When in inventory WITHOUT suit worn: passively exposes player to absorbed strains.
 * Contamination is absorbed while worn — harmless until removed without autoclaving.
 *
 * NBT keys:
 *   Contaminated: boolean
 *   ContaminationLevel: int 0–4 (STERILE/LOW/MODERATE/HIGH/HAZARDOUS)
 *   AbsorbedStrains: long[] — strain IDs absorbed while worn
 */
public class HazmatArmorItem extends ArmorItem {

    private static final String NBT_CONTAMINATED       = "Contaminated";
    private static final String NBT_CONTAMINATION_LEVEL = "ContaminationLevel";
    private static final String NBT_ABSORBED_STRAINS   = "AbsorbedStrains";

    public enum ContaminationLevel {
        STERILE(0, "Sterile", ChatFormatting.GREEN),
        LOW(1, "Low Contamination", ChatFormatting.YELLOW),
        MODERATE(2, "Moderate Contamination", ChatFormatting.GOLD),
        HIGH(3, "High Contamination", ChatFormatting.RED),
        HAZARDOUS(4, "HAZARDOUS", ChatFormatting.DARK_RED);

        public final int value;
        public final String label;
        public final ChatFormatting color;
        ContaminationLevel(int v, String l, ChatFormatting c) { value = v; label = l; color = c; }

        public static ContaminationLevel fromValue(int v) {
            for (ContaminationLevel cl : values()) if (cl.value == v) return cl;
            return STERILE;
        }
    }

    public HazmatArmorItem(ArmorItem.Type type, Properties props) {
        super(HazmatArmorMaterial.HAZMAT, type, props);
    }

    // ---- Contamination ----

    public static boolean isContaminated(ItemStack stack) {
        return NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).getBoolean(NBT_CONTAMINATED);
    }

    public static ContaminationLevel getContaminationLevel(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return ContaminationLevel.STERILE;
        return ContaminationLevel.fromValue(NbtHelper.getTag(stack).getInt(NBT_CONTAMINATION_LEVEL));
    }

    public static void setContaminationLevel(ItemStack stack, ContaminationLevel level) {
        NbtHelper.updateTag(stack, tag -> tag.putInt(NBT_CONTAMINATION_LEVEL, level.value));
    }

    public static long[] getAbsorbedStrains(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return new long[0];
        return NbtHelper.getTag(stack).getLongArray(NBT_ABSORBED_STRAINS);
    }

    /**
     * Absorb a strain into this piece (called when a transmission is blocked).
     * Increases contamination level over time.
     */
    public static void absorbStrain(ItemStack stack, long strainId) {
        NbtHelper.updateTag(stack, tag -> {
            // Add to absorbed list
            long[] current = tag.getLongArray(NBT_ABSORBED_STRAINS);
            // Check not already present
            for (long id : current) if (id == strainId) return;
            long[] updated = new long[current.length + 1];
            System.arraycopy(current, 0, updated, 0, current.length);
            updated[current.length] = strainId;
            tag.putLongArray(NBT_ABSORBED_STRAINS, updated);

            // Increase contamination level
            int level = tag.getInt(NBT_CONTAMINATION_LEVEL);
            int newLevel = Math.min(4, level + 1);
            tag.putInt(NBT_CONTAMINATION_LEVEL, newLevel);
            tag.putBoolean(NBT_CONTAMINATED, newLevel > 0);
        });
    }

    /** Sterilize this piece completely — called by Autoclave. */
    public static void sterilize(ItemStack stack) {
        NbtHelper.updateTag(stack, tag -> {
            tag.putBoolean(NBT_CONTAMINATED, false);
            tag.putInt(NBT_CONTAMINATION_LEVEL, 0);
            tag.putLongArray(NBT_ABSORBED_STRAINS, new long[0]);
        });
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        ContaminationLevel level = getContaminationLevel(stack);
        lines.add(Component.literal("§7Contamination: ")
            .append(Component.literal(level.label).withStyle(level.color)));

        if (level == ContaminationLevel.STERILE) {
            lines.add(Component.literal("§7Safe to store. Full set blocks all biological transmission."));
        } else {
            long[] strains = getAbsorbedStrains(stack);
            lines.add(Component.literal("§7Absorbed strains: §f" + strains.length));
            lines.add(Component.literal("§c⚠ Autoclave before removing suit!"));
            if (level == ContaminationLevel.HAZARDOUS) {
                lines.add(Component.literal("§4§l⚠ HAZARDOUS — double exposure rate in inventory!"));
            }
        }

        lines.add(Component.literal("§7§oMinimal physical protection."));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isContaminated(stack);
    }
}
