package com.grace.micromayhem.blockentity;

import net.minecraft.world.entity.player.Player;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.menu.CentrifugeMenu;
import com.grace.micromayhem.microbe.MicrobeRegistry;
import com.grace.micromayhem.microbe.MicrobeStrain;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.registry.ModItems;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CentrifugeBlockEntity extends BaseContainerBlockEntity {

    private final SimpleContainer items = new SimpleContainer(CentrifugeMenu.CONTAINER_SLOTS) {
        @Override public void setChanged() { super.setChanged(); CentrifugeBlockEntity.this.setChanged(); }
    };

    private int progress = 0;
    private int maxProgress = 0;
    private static final int PROCESS_TIME = 60;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> maxProgress; default -> 0; }; }
        @Override public void set(int i, int v) { switch(i) { case 0 -> progress = v; case 1 -> maxProgress = v; } }
        @Override public int getCount() { return CentrifugeMenu.DATA_COUNT; }
    };

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!be.canProcess()) { be.progress = 0; be.maxProgress = 0; return; }
        be.maxProgress = PROCESS_TIME;
        be.progress++;
        be.setChanged();
        if (be.progress >= be.maxProgress) be.process(sl);
    }

    private boolean canProcess() {
        ItemStack input = items.getItem(CentrifugeMenu.SLOT_INPUT);
        if (!(input.getItem() instanceof PetriDishItem)) return false;
        if (PetriDishItem.getState(input) != PetriDishItem.DishState.READY) return false;
        long[] ids = PetriDishItem.getStrainIds(input);
        if (ids.length <= 1) return false;
        // Count empty input dishes
        int emptyIn = 0;
        for (int i = CentrifugeMenu.SLOT_EMPTY_START; i < CentrifugeMenu.SLOT_EMPTY_START + CentrifugeMenu.SLOT_EMPTY_COUNT; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY) emptyIn++;
        }
        // Need (strainCount - 1) empty dishes and enough free output slots
        int needed = ids.length - 1;
        int freeOut = 0;
        for (int i = CentrifugeMenu.SLOT_OUTPUT_START; i < CentrifugeMenu.SLOT_OUTPUT_START + CentrifugeMenu.SLOT_OUTPUT_COUNT; i++)
            if (items.getItem(i).isEmpty()) freeOut++;
        return emptyIn >= needed && freeOut >= ids.length;
    }

    private void process(ServerLevel level) {
        progress = 0; maxProgress = 0;
        ItemStack input = items.getItem(CentrifugeMenu.SLOT_INPUT);
        long[] ids = PetriDishItem.getStrainIds(input);
        MicrobeRegistry registry = MicrobeRegistry.get(level);

        // Consume empty input dishes (N-1 of them)
        int toConsume = ids.length - 1;
        for (int i = CentrifugeMenu.SLOT_EMPTY_START; i < CentrifugeMenu.SLOT_EMPTY_START + CentrifugeMenu.SLOT_EMPTY_COUNT && toConsume > 0; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY) {
                s.shrink(1); toConsume--;
            }
        }

        // Fill output slots
        int outSlot = CentrifugeMenu.SLOT_OUTPUT_START;
        for (long id : ids) {
            MicrobeStrain strain = registry.getStrain(id);
            if (strain == null) continue;
            while (outSlot < CentrifugeMenu.SLOT_OUTPUT_START + CentrifugeMenu.SLOT_OUTPUT_COUNT && !items.getItem(outSlot).isEmpty()) outSlot++;
            if (outSlot >= CentrifugeMenu.SLOT_OUTPUT_START + CentrifugeMenu.SLOT_OUTPUT_COUNT) break;
            ItemStack dish = new ItemStack(ModItems.PETRI_DISH.get());
            final long finalId = id;
            NbtHelper.updateTag(dish, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{finalId});
                tag.putBoolean("Mutated", false);
            });
            items.setItem(outSlot, dish);
            outSlot++;
        }
        input.shrink(1);
        setChanged();
    }

    @Override public Component getDefaultName() { return Component.literal("Centrifuge"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new CentrifugeMenu(id, inv, items, data, this); }
    @Override public int getContainerSize() { return items.getContainerSize(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int count) { return items.removeItem(slot, count); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return items.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.setItem(slot, stack); }
    @Override public boolean stillValid(Player player) { return items.stillValid(player); }
    @Override public void clearContent() { items.clearContent(); }
    @Override public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, items.getItem(i));
        return list;
    }
    @Override public void setItems(NonNullList<ItemStack> list) { for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i)); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list, reg);
        tag.putInt("Progress", progress); tag.putInt("MaxProgress", maxProgress);
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, reg);
        for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i));
        progress = tag.getInt("Progress"); maxProgress = tag.getInt("MaxProgress");
    }
}
