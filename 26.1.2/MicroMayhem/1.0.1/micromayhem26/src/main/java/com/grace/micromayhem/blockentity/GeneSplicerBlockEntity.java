package com.grace.micromayhem.blockentity;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.menu.GeneSplicerMenu;
import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.registry.ModItems;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

public class GeneSplicerBlockEntity extends BaseContainerBlockEntity {

    private static final int CANDIDATE_COUNT = 3;

    private final SimpleContainer items = new SimpleContainer(GeneSplicerMenu.CONTAINER_SLOTS) {
        @Override public void setChanged() { super.setChanged(); GeneSplicerBlockEntity.this.setChanged(); }
    };

    private int progress = 0;
    private int maxProgress = 0;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> maxProgress; default -> 0; }; }
        @Override public void set(int i, int v) { switch(i) { case 0 -> progress = v; case 1 -> maxProgress = v; } }
        @Override public int getCount() { return GeneSplicerMenu.DATA_COUNT; }
    };

    public GeneSplicerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENE_SPLICER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GeneSplicerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.maxProgress == 0 && be.canStart(sl)) { be.startProcessing(sl); return; }
        if (be.maxProgress == 0) return;
        be.progress++;
        be.setChanged();
        if (be.progress >= be.maxProgress) be.complete(sl);
    }

    private boolean canStart(ServerLevel level) {
        ItemStack a = items.getItem(GeneSplicerMenu.SLOT_DISH_A);
        ItemStack b = items.getItem(GeneSplicerMenu.SLOT_DISH_B);
        if (!(a.getItem() instanceof PetriDishItem) || !(b.getItem() instanceof PetriDishItem)) return false;
        if (PetriDishItem.getState(a) != PetriDishItem.DishState.READY) return false;
        if (PetriDishItem.getState(b) != PetriDishItem.DishState.READY) return false;
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain sa = getFirstStrain(a, registry);
        MicrobeStrain sb = getFirstStrain(b, registry);
        if (GeneticRecombination.validatePair(sa, sb) != null) return false;
        int empty = 0;
        for (int i = GeneSplicerMenu.SLOT_EMPTY_START; i < GeneSplicerMenu.SLOT_EMPTY_START + GeneSplicerMenu.SLOT_EMPTY_COUNT; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY) empty++;
        }
        int freeOut = 0;
        for (int i = GeneSplicerMenu.SLOT_OUT_START; i < GeneSplicerMenu.SLOT_OUT_START + GeneSplicerMenu.SLOT_OUT_COUNT; i++)
            if (items.getItem(i).isEmpty()) freeOut++;
        return empty >= CANDIDATE_COUNT && freeOut >= CANDIDATE_COUNT;
    }

    private void startProcessing(ServerLevel level) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain a = getFirstStrain(items.getItem(GeneSplicerMenu.SLOT_DISH_A), registry);
        MicrobeStrain b = getFirstStrain(items.getItem(GeneSplicerMenu.SLOT_DISH_B), registry);
        maxProgress = (a != null && b != null) ? GeneticRecombination.spliceDuration(a, b) : 200;
        progress = 0;
        setChanged();
    }

    private void complete(ServerLevel level) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain parentA = getFirstStrain(items.getItem(GeneSplicerMenu.SLOT_DISH_A), registry);
        MicrobeStrain parentB = getFirstStrain(items.getItem(GeneSplicerMenu.SLOT_DISH_B), registry);
        if (parentA == null || parentB == null) { reset(); return; }

        long seed = level.getSeed() ^ level.getGameTime();
        List<MicrobeStrain> candidates = GeneticRecombination.recombine(parentA, parentB, seed, registry);
        if (candidates.isEmpty()) { reset(); return; }

        int toConsume = CANDIDATE_COUNT;
        for (int i = GeneSplicerMenu.SLOT_EMPTY_START; i < GeneSplicerMenu.SLOT_EMPTY_START + GeneSplicerMenu.SLOT_EMPTY_COUNT && toConsume > 0; i++) {
            ItemStack s = items.getItem(i);
            if (s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY) {
                s.shrink(1); toConsume--;
            }
        }

        int outSlot = GeneSplicerMenu.SLOT_OUT_START;
        int produced = 0;
        for (MicrobeStrain child : candidates) {
            while (outSlot < GeneSplicerMenu.SLOT_OUT_START + GeneSplicerMenu.SLOT_OUT_COUNT && !items.getItem(outSlot).isEmpty()) outSlot++;
            if (outSlot >= GeneSplicerMenu.SLOT_OUT_START + GeneSplicerMenu.SLOT_OUT_COUNT) break;
            final long cid = child.strainId;
            ItemStack result = new ItemStack(ModItems.PETRI_DISH.get());
            NbtHelper.updateTag(result, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{cid});
                tag.putBoolean("Recombinant", true);
            });
            items.setItem(outSlot, result);
            outSlot++; produced++;
        }

        items.getItem(GeneSplicerMenu.SLOT_DISH_A).shrink(1);
        items.getItem(GeneSplicerMenu.SLOT_DISH_B).shrink(1);
        final int fp = produced;
        level.players().stream()
            .filter(p -> p.distanceToSqr(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()) < 100)
            .forEach(p -> p.sendSystemMessage(Component.literal("§2[Splicer] §f" + fp + " §2recombinants ready.")));
        reset();
    }

    private void reset() { progress = 0; maxProgress = 0; setChanged(); }

    private static MicrobeStrain getFirstStrain(ItemStack dish, MicrobeRegistry registry) {
        long[] ids = PetriDishItem.getStrainIds(dish);
        return ids.length == 0 ? null : registry.getStrain(ids[0]);
    }

    @Override public Component getDefaultName() { return Component.literal("Gene Splicer"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new GeneSplicerMenu(id, inv, items, data, this); }
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList itemSlots = output.childrenList("Items");
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack stack = items.getItem(i);
            if (stack.isEmpty()) continue;
            ValueOutput slot = itemSlots.addChild();
            slot.putByte("Slot", (byte) i);
            slot.store("Stack", ItemStack.CODEC, stack);
        }
        output.putInt("Progress", progress);
        output.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clearContent();
        items.clearContent();
        for (ValueInput slot : input.childrenListOrEmpty("Items")) {
            int slotIdx = slot.getByteOr("Slot", (byte) -1) & 0xFF;
            if (slotIdx >= 0 && slotIdx < items.getContainerSize()) {
                slot.read("Stack", ItemStack.CODEC).ifPresent(stack -> items.setItem(slotIdx, stack));
            }
        }
        progress = input.getIntOr("Progress", 0);
        maxProgress = input.getIntOr("MaxProgress", 0);
    }
}
