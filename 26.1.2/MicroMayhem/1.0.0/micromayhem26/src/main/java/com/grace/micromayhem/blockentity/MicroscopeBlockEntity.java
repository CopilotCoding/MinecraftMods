package com.grace.micromayhem.blockentity;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.item.SwabItem;
import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MicroscopeBlockEntity extends BlockEntity implements MenuProvider {

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROSCOPE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() { return Component.literal("Microscope"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        if (player.level() instanceof ServerLevel serverLevel) performReadout(player, serverLevel);
        return null;
    }

    private void performReadout(Player player, ServerLevel level) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) hand = player.getOffhandItem();

        List<MicrobeStrain> strains = new ArrayList<>();
        MicrobeRegistry registry = MicrobeRegistry.get(level);

        if (SwabItem.isContaminated(hand)) {
            long[] ids = NbtHelper.getTag(hand).getLongArray(SwabItem.NBT_STRAIN_IDS).orElse(new long[0]);
            for (long id : ids) { MicrobeStrain s = registry.getStrain(id); if (s != null) strains.add(s); }
        } else if (hand.getItem() instanceof PetriDishItem && PetriDishItem.getState(hand) == PetriDishItem.DishState.READY) {
            long[] ids = PetriDishItem.getStrainIds(hand);
            for (long id : ids) { MicrobeStrain s = registry.getStrain(id); if (s != null) strains.add(s); }
        } else {
            player.sendSystemMessage(Component.literal("§7[Microscope] No valid sample. Hold a Swab or Petri Dish."));
            return;
        }

        if (strains.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[Microscope] Sample yielded no detectable organisms."));
            return;
        }

        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§6§lMICROSCOPE ANALYSIS — " + strains.size() + " organism(s)"));
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));

        MicrobeCodex codex = new MicrobeCodex();
        codex.loadFromTag(player.getPersistentData());
        int newDiscoveries = 0;

        for (int i = 0; i < strains.size(); i++) {
            MicrobeStrain strain = strains.get(i);
            boolean wasCharacterised = strain.fullyCharacterised;
            strain.recordMicroscopeView();
            if (!wasCharacterised && strain.fullyCharacterised) {
                player.sendSystemMessage(Component.literal(
                    "§b✦ Full characterisation achieved for " + strain.getScientificName() + "!"));
            }
            player.sendSystemMessage(Component.literal("§7[Organism " + (i + 1) + "/" + strains.size() + "]" +
                (strain.isWorldStrain && !strain.fullyCharacterised
                    ? " §7(View " + strain.microscopeViewCount + "/3)" : "")));
            for (String line : strain.getMicroscopeReadout()) player.sendSystemMessage(Component.literal(line));
            if (!codex.hasDiscovered(strain.strainId)) {
                codex.discover(strain.strainId);
                newDiscoveries++;
                player.sendSystemMessage(Component.literal("§b✦ New organism catalogued! Codex: " + codex.count()));
            }
            player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        }

        registry.setDirty();
        codex.saveToTag(player.getPersistentData());
        if (newDiscoveries > 0)
            player.sendSystemMessage(Component.literal("§a§l+" + newDiscoveries + " new strain(s) added to your Codex."));
    }

    @Override
    protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); }

    @Override
    protected void loadAdditional(ValueInput input) { super.loadAdditional(input); }
}
