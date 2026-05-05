package com.grace.micromayhem.blockentity;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.item.SwabItem;
import com.grace.micromayhem.microbe.PlayerMicrobiome;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class AutoclaveBlockEntity extends BlockEntity implements MenuProvider {

    public AutoclaveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOCLAVE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() { return Component.literal("Autoclave"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        if (!player.level().isClientSide) sterilize(player);
        return null;
    }

    private void sterilize(Player player) {
        ItemStack hand = player.getMainHandItem();

        if (hand.isEmpty()) {
            // Full decontamination via new HostMicrobiome system
            com.grace.micromayhem.microbe.HostMicrobiome microbiome =
                new com.grace.micromayhem.microbe.HostMicrobiome();
            microbiome.loadFromTag(player.getPersistentData());
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.grace.micromayhem.microbe.MicrobeRegistry registry =
                    com.grace.micromayhem.microbe.MicrobeRegistry.get(sl);
                microbiome.clearAll(registry);
            }
            microbiome.saveToTag(player.getPersistentData());
            player.sendSystemMessage(Component.literal("§a[Autoclave] Full decontamination. All infections cleared."));
            return;
        }

        // Sterilize hazmat armor
        if (hand.getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem) {
            com.grace.micromayhem.item.HazmatArmorItem.sterilize(hand);
            player.sendSystemMessage(Component.literal("§a[Autoclave] Hazmat piece sterilized and safe to remove."));
            return;
        }

        if (SwabItem.isContaminated(hand)) {
            NbtHelper.removeTag(hand);
            player.sendSystemMessage(Component.literal("§a[Autoclave] Swab sterilized."));
            return;
        }

        if (hand.getItem() instanceof PetriDishItem) {
            NbtHelper.removeTag(hand);
            player.sendSystemMessage(Component.literal("§a[Autoclave] Petri dish sterilized and reset."));
            return;
        }

        player.sendSystemMessage(Component.literal(
            "§7[Autoclave] Hold a contaminated Swab, Petri Dish, or empty hand to decontaminate self."));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); }
}
