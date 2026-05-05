package com.grace.micromayhem.blockentity;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.microbe.MicrobeRegistry;
import com.grace.micromayhem.microbe.MicrobeStrain;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.registry.ModItems;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CentrifugeBlockEntity extends BlockEntity implements MenuProvider {

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() { return Component.literal("Centrifuge"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        if (player.level() instanceof ServerLevel serverLevel) processSample(player, serverLevel);
        return null;
    }

    private void processSample(Player player, ServerLevel level) {
        ItemStack hand = player.getMainHandItem();

        if (!(hand.getItem() instanceof PetriDishItem)
                || PetriDishItem.getState(hand) != PetriDishItem.DishState.READY) {
            player.sendSystemMessage(Component.literal("§7[Centrifuge] Insert a ready Petri Dish (hold in main hand)."));
            return;
        }

        long[] ids = PetriDishItem.getStrainIds(hand);
        if (ids.length <= 1) {
            player.sendSystemMessage(Component.literal("§7[Centrifuge] Only one strain present — no isolation needed."));
            return;
        }

        MicrobeRegistry registry = MicrobeRegistry.get(level);
        List<ItemStack> outputs = new ArrayList<>();

        for (long id : ids) {
            MicrobeStrain strain = registry.getStrain(id);
            if (strain == null) continue;
            ItemStack dish = new ItemStack(ModItems.PETRI_DISH.get());
            final long finalId = id;
            NbtHelper.updateTag(dish, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{finalId});
                tag.putBoolean("Mutated", false);
            });
            outputs.add(dish);
        }

        hand.shrink(1);

        for (ItemStack out : outputs) {
            ItemEntity ie = new ItemEntity(level,
                getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.0, getBlockPos().getZ() + 0.5, out);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }

        player.sendSystemMessage(Component.literal("§a[Centrifuge] Isolated §f" + outputs.size() + " §astrains."));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); }
}
