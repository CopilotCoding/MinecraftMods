package com.grace.micromayhem.blockentity;

import net.minecraft.world.entity.player.Player;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.menu.IrradiatorMenu;
import com.grace.micromayhem.microbe.*;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class IrradiatorBlockEntity extends BaseContainerBlockEntity {

    private static final int CANDIDATE_COUNT = 4;

    private final SimpleContainer items = new SimpleContainer(IrradiatorMenu.CONTAINER_SLOTS) {
        @Override public void setChanged() { super.setChanged(); IrradiatorBlockEntity.this.setChanged(); }
    };

    private int progress = 0;
    private int maxProgress = 0;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> maxProgress; default -> 0; }; }
        @Override public void set(int i, int v) { switch(i) { case 0 -> progress = v; case 1 -> maxProgress = v; } }
        @Override public int getCount() { return IrradiatorMenu.DATA_COUNT; }
    };

    public IrradiatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRRADIATOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IrradiatorBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.maxProgress == 0 && be.canStart()) { be.startProcessing(); return; }
        if (be.maxProgress == 0) return;
        be.progress++;
        be.setChanged();
        if (be.progress >= be.maxProgress) be.complete(sl);
    }

    private boolean canStart() {
        if (!(items.getItem(IrradiatorMenu.SLOT_DISH).getItem() instanceof PetriDishItem)) return false;
        if (PetriDishItem.getState(items.getItem(IrradiatorMenu.SLOT_DISH)) != PetriDishItem.DishState.READY) return false;
        if (!items.getItem(IrradiatorMenu.SLOT_FUEL).is(Items.GLOWSTONE_DUST)) return false;
        // Count empty dishes in input slots
        int empty = 0;
        for (int i = IrradiatorMenu.SLOT_EMPTY_START; i < IrradiatorMenu.SLOT_EMPTY_START + IrradiatorMenu.SLOT_EMPTY_COUNT; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY)
                empty++;
        }
        // Need enough empty dishes and enough free output slots
        int freeOut = 0;
        for (int i = IrradiatorMenu.SLOT_OUT_START; i < IrradiatorMenu.SLOT_OUT_START + IrradiatorMenu.SLOT_OUT_COUNT; i++)
            if (items.getItem(i).isEmpty()) freeOut++;
        return empty >= CANDIDATE_COUNT && freeOut >= CANDIDATE_COUNT;
    }

    private void startProcessing() {
        maxProgress = 200;
        progress = 0;
        items.getItem(IrradiatorMenu.SLOT_FUEL).shrink(1);
        setChanged();
    }

    private void complete(ServerLevel level) {
        ItemStack dishStack = items.getItem(IrradiatorMenu.SLOT_DISH);
        long[] ids = PetriDishItem.getStrainIds(dishStack);
        if (ids.length == 0) { reset(); return; }

        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain parent = registry.getStrain(ids[0]);
        if (parent == null) { reset(); return; }

        EvolutionPressure pressure = EvolutionPressure.fromItem(items.getItem(IrradiatorMenu.SLOT_PRESSURE));
        Random rng = new Random(level.getSeed() ^ level.getGameTime());

        // Consume empty dish inputs
        int toConsume = CANDIDATE_COUNT;
        for (int i = IrradiatorMenu.SLOT_EMPTY_START; i < IrradiatorMenu.SLOT_EMPTY_START + IrradiatorMenu.SLOT_EMPTY_COUNT && toConsume > 0; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY) {
                s.shrink(1); toConsume--;
            }
        }

        // Fill output slots
        int outSlot = IrradiatorMenu.SLOT_OUT_START;
        int produced = 0;
        for (int c = 0; c < CANDIDATE_COUNT; c++) {
            while (outSlot < IrradiatorMenu.SLOT_OUT_START + IrradiatorMenu.SLOT_OUT_COUNT && !items.getItem(outSlot).isEmpty()) outSlot++;
            if (outSlot >= IrradiatorMenu.SLOT_OUT_START + IrradiatorMenu.SLOT_OUT_COUNT) break;

            MicrobeStrain child = parent.mutate(parent.strainId ^ rng.nextLong());
            if (pressure != null && rng.nextFloat() < 0.8f) pressure.applyBias(child, rng);
            registry.addStrain(child);

            final long cid = child.strainId;
            ItemStack result = new ItemStack(ModItems.PETRI_DISH.get());
            NbtHelper.updateTag(result, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{cid});
                tag.putBoolean("Mutated", true);
            });
            items.setItem(outSlot, result);
            outSlot++; produced++;
        }

        dishStack.shrink(1);
        items.getItem(IrradiatorMenu.SLOT_PRESSURE).shrink(1);
        final int fp = produced;
        final String fn = pressure != null ? pressure.displayName : "random";
        level.players().stream()
            .filter(p -> p.distanceToSqr(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()) < 100)
            .forEach(p -> p.sendSystemMessage(Component.literal("§5[Irradiator] §f" + fp + " §5candidates ready. Pressure: §d" + fn)));
        reset();
    }

    private void reset() { progress = 0; maxProgress = 0; setChanged(); }

    @Override public Component getDefaultName() { return Component.literal("Irradiator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new IrradiatorMenu(id, inv, items, data, this); }
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
