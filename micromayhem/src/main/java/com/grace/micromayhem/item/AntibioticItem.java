package com.grace.micromayhem.item;

import com.grace.micromayhem.microbe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class AntibioticItem extends Item {

    public enum TreatmentType {
        ANTIBIOTIC("Broad-Spectrum Antibiotic", MicrobeType.BACTERIA, ChatFormatting.AQUA),
        ANTIVIRAL("Antiviral Agent", MicrobeType.VIRUS, ChatFormatting.LIGHT_PURPLE),
        ANTIFUNGAL("Antifungal Compound", MicrobeType.FUNGUS, ChatFormatting.YELLOW),
        ANTIPARASITIC("Antiparasitic Drug", MicrobeType.PARASITE, ChatFormatting.GREEN);

        public final String displayName;
        public final MicrobeType targets;
        public final ChatFormatting color;
        TreatmentType(String n, MicrobeType t, ChatFormatting c) { displayName = n; targets = t; color = c; }
    }

    private final TreatmentType treatment;

    public AntibioticItem(TreatmentType treatment, Properties props) {
        super(props);
        this.treatment = treatment;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            MicrobeRegistry registry = MicrobeRegistry.get(serverLevel);
            HostMicrobiome microbiome = new HostMicrobiome();
            microbiome.loadFromTag(player.getPersistentData());
            microbiome.clearAllOfType(treatment.targets, registry);
            microbiome.saveToTag(player.getPersistentData());
            // Antibiotic clearance — no immune bonus, slight ceiling penalty
            PlayerImmuneSystem immune = new PlayerImmuneSystem();
            immune.loadFromTag(player.getPersistentData());
            immune.onAntibioticClearance();
            immune.saveToTag(player.getPersistentData());
            player.displayClientMessage(
                Component.literal(treatment.displayName + " administered. ")
                    .append(Component.literal(treatment.targets.displayName + " infections cleared.").withStyle(treatment.color)), true);
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("§7Clears all active §f" + treatment.targets.displayName
            + "§7 infections on use.").withStyle(treatment.color));
        lines.add(Component.literal("§7Right-click to consume."));
    }
}
