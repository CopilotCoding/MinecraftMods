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

        // Sterilize held hazmat piece
        if (hand.getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem) {
            com.grace.micromayhem.item.HazmatArmorItem.sterilize(hand);
            player.sendSystemMessage(Component.literal("§a[Autoclave] Hazmat piece sterilized."));
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

        if (hand.isEmpty()) {
            // Sterilize all equipped hazmat pieces
            net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET
            };
            int sterilized = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : armorSlots) {
                ItemStack piece = player.getItemBySlot(slot);
                if (piece.getItem() instanceof com.grace.micromayhem.item.HazmatArmorItem) {
                    com.grace.micromayhem.item.HazmatArmorItem.sterilize(piece);
                    sterilized++;
                }
            }
            if (sterilized > 0) {
                player.sendSystemMessage(Component.literal(
                    "§a[Autoclave] " + sterilized + " hazmat piece(s) sterilized. Safe to remove."));
            } else {
                player.sendSystemMessage(Component.literal(
                    "§7[Autoclave] No hazmat pieces equipped to sterilize. Use antibiotics or a vaccine to treat infections."));
            }
            return;
        }

        player.sendSystemMessage(Component.literal(
            "§7[Autoclave] Hold a contaminated Swab, Petri Dish, or empty hand to decontaminate self and suit."));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); }
}
