package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.MicrobeStrain;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.List;

/**
 * Hazmat Suit armor piece.
 * In 26.1.2 ArmorItem is no longer the base — we extend Item directly and
 * handle equipment slot via the Equippable data component approach, or simply
 * keep it as a plain Item with durability and armor attributes via data components.
 *
 * For simplicity we keep it as a plain Item — the armor protection values are
 * minimal (1-2 defense) so the loss of the ArmorItem base is acceptable.
 * The contamination/biological-protection logic is entirely in code, not stat-based.
 */
public class HazmatArmorItem extends Item {

    private static final String NBT_CONTAMINATED        = "Contaminated";
    private static final String NBT_CONTAMINATION_LEVEL = "ContaminationLevel";
    private static final String NBT_ABSORBED_STRAINS    = "AbsorbedStrains";

    private final ArmorType armorType;

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

    public HazmatArmorItem(ArmorType type, Properties props) {
        super(props.component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
            .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
            .build()));
        this.armorType = type;
    }

    public ArmorType getArmorType() { return armorType; }

    // ---- Contamination ----

    public static boolean isContaminated(ItemStack stack) {
        return NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).getBooleanOr(NBT_CONTAMINATED, false);
    }

    public static ContaminationLevel getContaminationLevel(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return ContaminationLevel.STERILE;
        return ContaminationLevel.fromValue(NbtHelper.getTag(stack).getIntOr(NBT_CONTAMINATION_LEVEL, 0));
    }

    public static void setContaminationLevel(ItemStack stack, ContaminationLevel level) {
        NbtHelper.updateTag(stack, tag -> tag.putInt(NBT_CONTAMINATION_LEVEL, level.value));
    }

    public static long[] getAbsorbedStrains(ItemStack stack) {
        if (!NbtHelper.hasTag(stack)) return new long[0];
        return NbtHelper.getTag(stack).getLongArray(NBT_ABSORBED_STRAINS).orElse(new long[0]);
    }

    public static void absorbStrain(ItemStack stack, long strainId) {
        NbtHelper.updateTag(stack, tag -> {
            long[] current = tag.getLongArray(NBT_ABSORBED_STRAINS).orElse(new long[0]);
            for (long id : current) if (id == strainId) return;
            long[] updated = new long[current.length + 1];
            System.arraycopy(current, 0, updated, 0, current.length);
            updated[current.length] = strainId;
            tag.putLongArray(NBT_ABSORBED_STRAINS, updated);

            int level = tag.getIntOr(NBT_CONTAMINATION_LEVEL, 0);
            int newLevel = Math.min(4, level + 1);
            tag.putInt(NBT_CONTAMINATION_LEVEL, newLevel);
            tag.putBoolean(NBT_CONTAMINATED, newLevel > 0);
        });
    }

    public static void sterilize(ItemStack stack) {
        NbtHelper.updateTag(stack, tag -> {
            tag.putBoolean(NBT_CONTAMINATED, false);
            tag.putInt(NBT_CONTAMINATION_LEVEL, 0);
            tag.putLongArray(NBT_ABSORBED_STRAINS, new long[0]);
        });
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        ContaminationLevel level = getContaminationLevel(stack);
        lines.accept(Component.literal("§7Contamination: ")
            .append(Component.literal(level.label).withStyle(level.color)));

        if (level == ContaminationLevel.STERILE) {
            lines.accept(Component.literal("§7Safe to store. Full set blocks all biological transmission."));
        } else {
            long[] strains = getAbsorbedStrains(stack);
            lines.accept(Component.literal("§7Absorbed strains: §f" + strains.length));
            lines.accept(Component.literal("§c⚠ Autoclave before removing suit!"));
            if (level == ContaminationLevel.HAZARDOUS) {
                lines.accept(Component.literal("§4§l⚠ HAZARDOUS — double exposure rate in inventory!"));
            }
        }

        lines.accept(Component.literal("§7§oMinimal physical protection."));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isContaminated(stack);
    }
}
