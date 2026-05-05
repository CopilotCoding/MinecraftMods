package com.grace.micromayhem.blockentity;

import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.microbe.*;
import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.registry.ModItems;
import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Irradiator Block Entity.
 *
 * Slots:
 *   0 — Petri Dish (READY state)
 *   1 — Pressure item (optional, biases mutation direction)
 *   2 — Fuel (glowstone dust, consumed per cycle)
 *
 * Cycle:
 *   1. Player right-clicks → opens "menu" (chat-based interaction, no screen needed)
 *   2. Player inserts items by holding them and right-clicking
 *   3. Alternatively: right-click with empty hand to START the cycle if slots are filled
 *   4. Cycle runs for (30 - accumulator*20) seconds, floored at 5s
 *   5. On completion: drops 3–5 mutant candidate dishes at the block
 *   6. Player picks up desired candidate, autoclave the rest
 *
 * Because we don't have a custom GUI, interaction is menu-less:
 *   - Hold dish + right-click → loads dish slot
 *   - Hold pressure item + right-click → loads pressure slot
 *   - Hold glowstone + right-click → loads fuel slot
 *   - Empty hand + right-click → shows status / starts cycle
 */
public class IrradiatorBlockEntity extends BlockEntity implements MenuProvider {

    // Slot logical indices (stored in NBT, not a real container)
    private ItemStack dishSlot     = ItemStack.EMPTY;
    private ItemStack pressureSlot = ItemStack.EMPTY;
    private ItemStack fuelSlot     = ItemStack.EMPTY;

    // Cycle state
    private boolean running     = false;
    private int     ticksRemaining = 0;
    private int     cycleDuration  = 0;

    private static final int CANDIDATE_COUNT = 4; // how many variants to produce

    public IrradiatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRRADIATOR.get(), pos, state);
    }

    // ---- Tick ----

    public static void tick(Level level, BlockPos pos, BlockState state, IrradiatorBlockEntity be) {
        if (!be.running) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        be.ticksRemaining--;
        if (be.ticksRemaining % 20 == 0) be.setChanged(); // save progress each second

        if (be.ticksRemaining <= 0) {
            be.completeIrradiation(serverLevel, pos);
        }
    }

    // ---- Interaction (menu-less, chat-based) ----

    @Override
    public Component getDisplayName() { return Component.literal("Irradiator"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        // All interaction is chat-based via right-click
        handleInteraction(player);
        return null;
    }

    private void handleInteraction(Player player) {
        ItemStack held = player.getMainHandItem();

        if (running) {
            int secsLeft = ticksRemaining / 20;
            player.sendSystemMessage(Component.literal(
                "§e[Irradiator] Cycle in progress — " + secsLeft + "s remaining."));
            return;
        }

        // Load dish slot
        if (held.getItem() instanceof PetriDishItem
                && PetriDishItem.getState(held) == PetriDishItem.DishState.READY
                && dishSlot.isEmpty()) {
            dishSlot = held.copy();
            held.shrink(1);
            player.sendSystemMessage(Component.literal("§a[Irradiator] Petri dish loaded."));
            setChanged(); return;
        }

        // Load fuel
        if (held.is(Items.GLOWSTONE_DUST) && fuelSlot.isEmpty()) {
            fuelSlot = new ItemStack(Items.GLOWSTONE_DUST, 1);
            held.shrink(1);
            player.sendSystemMessage(Component.literal("§a[Irradiator] Glowstone fuel loaded."));
            setChanged(); return;
        }

        // Load pressure item (anything that matches a pressure)
        if (!held.isEmpty() && EvolutionPressure.fromItem(held) != null && pressureSlot.isEmpty()) {
            EvolutionPressure p = EvolutionPressure.fromItem(held);
            pressureSlot = held.copy();
            held.shrink(1);
            player.sendSystemMessage(Component.literal(
                "§a[Irradiator] Pressure loaded: §f" + p.displayName));
            player.sendSystemMessage(Component.literal("§7" + p.description));
            setChanged(); return;
        }

        // Show status or start cycle
        if (held.isEmpty()) {
            showStatus(player);
        }
    }

    private void showStatus(Player player) {
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§5§lIRRADIATOR STATUS"));
        player.sendSystemMessage(Component.literal(
            "§7Dish:     §f" + (dishSlot.isEmpty() ? "§c[empty]" : "Petri dish loaded")));
        player.sendSystemMessage(Component.literal(
            "§7Fuel:     §f" + (fuelSlot.isEmpty() ? "§c[empty — needs glowstone dust]" : "Glowstone loaded")));

        if (pressureSlot.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Pressure: §7[none — random mutation]"));
        } else {
            EvolutionPressure p = EvolutionPressure.fromItem(pressureSlot);
            player.sendSystemMessage(Component.literal(
                "§7Pressure: §d" + (p != null ? p.displayName : pressureSlot.getHoverName().getString())));
        }

        // Show valid pressure items
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§7§lPressure items:"));
        for (EvolutionPressure p : EvolutionPressure.values()) {
            player.sendSystemMessage(Component.literal(
                "§7  " + p.triggerItem.getDefaultInstance().getHoverName().getString() + " → §d" + p.displayName));
        }
        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));

        if (!dishSlot.isEmpty() && !fuelSlot.isEmpty()) {
            // Can start — calculate duration
            long[] ids = PetriDishItem.getStrainIds(dishSlot);
            if (ids.length > 0 && level instanceof ServerLevel serverLevel) {
                MicrobeStrain strain = MicrobeRegistry.get(serverLevel).getStrain(ids[0]);
                if (strain != null) {
                    int dur = calculateDuration(strain);
                    player.sendSystemMessage(Component.literal(
                        "§aReady! Cycle will take §f" + (dur/20) + "s §a(strain accumulator: "
                        + String.format("%.2f", strain.mutationAccumulator) + ")."));
                    player.sendSystemMessage(Component.literal("§7Sneak + right-click to start."));
                }
            }
        } else {
            player.sendSystemMessage(Component.literal("§c⚠ Load a Petri Dish and Glowstone Dust to begin."));
        }
    }

    /** Called when player sneaks + right-clicks (handled from ModEvents). */
    public void startCycle(ServerPlayer player) {
        if (running) { player.sendSystemMessage(Component.literal("§e[Irradiator] Already running.")); return; }
        if (dishSlot.isEmpty()) { player.sendSystemMessage(Component.literal("§c[Irradiator] No dish loaded.")); return; }
        if (fuelSlot.isEmpty()) { player.sendSystemMessage(Component.literal("§c[Irradiator] No fuel. Needs glowstone dust.")); return; }

        long[] ids = PetriDishItem.getStrainIds(dishSlot);
        if (ids.length == 0) { player.sendSystemMessage(Component.literal("§c[Irradiator] Dish is empty.")); return; }

        MicrobeStrain strain = MicrobeRegistry.get((ServerLevel) level).getStrain(ids[0]);
        if (strain == null) { player.sendSystemMessage(Component.literal("§c[Irradiator] Strain not found.")); return; }

        cycleDuration = calculateDuration(strain);
        ticksRemaining = cycleDuration;
        running = true;

        // Consume fuel
        fuelSlot = ItemStack.EMPTY;

        EvolutionPressure pressure = EvolutionPressure.fromItem(pressureSlot);
        player.sendSystemMessage(Component.literal(
            "§5[Irradiator] Irradiation started! " +
            (pressure != null ? "Pressure: §d" + pressure.displayName + "§5. " : "No pressure (random). ") +
            "§5Duration: §f" + (cycleDuration/20) + "s"));
        setChanged();
    }

    // ---- Complete cycle ----

    private void completeIrradiation(ServerLevel level, BlockPos pos) {
        running = false;

        long[] ids = PetriDishItem.getStrainIds(dishSlot);
        if (ids.length == 0) { dishSlot = ItemStack.EMPTY; setChanged(); return; }

        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain parent = registry.getStrain(ids[0]);
        if (parent == null) { dishSlot = ItemStack.EMPTY; setChanged(); return; }

        EvolutionPressure pressure = EvolutionPressure.fromItem(pressureSlot);
        Random rng = new Random(level.getSeed() ^ level.getGameTime());

        List<MicrobeStrain> candidates = new ArrayList<>();
        for (int i = 0; i < CANDIDATE_COUNT; i++) {
            MicrobeStrain child = parent.mutate(parent.strainId ^ rng.nextLong());
            // Apply pressure bias if set — 80% chance bias applies per candidate
            if (pressure != null && rng.nextFloat() < 0.8f) {
                pressure.applyBias(child, rng);
            }
            registry.addStrain(child);
            candidates.add(child);
        }

        // Drop each candidate as its own petri dish at the block
        double dx = pos.getX() + 0.5, dy = pos.getY() + 1.1, dz = pos.getZ() + 0.5;
        for (int i = 0; i < candidates.size(); i++) {
            MicrobeStrain c = candidates.get(i);
            ItemStack dish = new ItemStack(ModItems.PETRI_DISH.get());
            final long cid = c.strainId;
            NbtHelper.updateTag(dish, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{cid});
                tag.putBoolean("Mutated", true);
                tag.putBoolean("IrradiatedCandidate", true);
            });

            // Spread them out slightly so they're easy to pick up individually
            double offsetX = (i - candidates.size() / 2.0) * 0.4;
            ItemEntity ie = new ItemEntity(level, dx + offsetX, dy, dz, dish);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }

        // Also announce to nearby players
        level.players().stream()
            .filter(p -> p.distanceToSqr(dx, dy, dz) < 100)
            .forEach(p -> {
                p.sendSystemMessage(Component.literal("§5[Irradiator] Cycle complete! §f" + candidates.size() + " §5mutant candidates ejected."));
                if (pressure != null) {
                    p.sendSystemMessage(Component.literal("§7Bias applied: §d" + pressure.displayName));
                }
                p.sendSystemMessage(Component.literal("§7Pick up the dish you want. Autoclave the rest."));
                // Show candidates in chat for easy comparison
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
                for (int i = 0; i < candidates.size(); i++) {
                    MicrobeStrain c = candidates.get(i);
                    String effectColor = c.effect.beneficial() ? "§a" : (c.effect == MicrobeEffect.NONE ? "§e" : "§c");
                    p.sendSystemMessage(Component.literal(
                        "§7Candidate " + (i+1) + ": §6" + c.getScientificName() +
                        " " + effectColor + c.effect.displayName +
                        " §7| Virulence: §f" + c.getVirulenceLabel() +
                        " §7| Trans: §f" + c.getTransmissibilityLabel() +
                        " §7| Gen." + c.generation
                    ));
                }
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
            });

        // Consume dish and pressure item
        dishSlot = ItemStack.EMPTY;
        pressureSlot = ItemStack.EMPTY;
        setChanged();
    }

    // ---- Duration calculation ----

    /**
     * Cycle duration based on strain's mutation accumulator.
     * Unstable strains (high accumulator) irradiate faster.
     * Stable strains (low accumulator, high generation) take longer.
     * Range: 5s (100 ticks) to 30s (600 ticks).
     */
    private static int calculateDuration(MicrobeStrain strain) {
        // accumulator is 0.0–1.0; generation adds stability
        float stability = 1.0f - strain.mutationAccumulator
            + (strain.generation * 0.05f); // higher generation = more stable
        stability = Math.max(0f, Math.min(1f, stability));
        int ticks = 100 + (int)(stability * 500); // 100–600 ticks = 5–30s
        return ticks;
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!dishSlot.isEmpty())     tag.put("DishSlot",     dishSlot.save(reg));
        if (!pressureSlot.isEmpty()) tag.put("PressureSlot", pressureSlot.save(reg));
        if (!fuelSlot.isEmpty())     tag.put("FuelSlot",     fuelSlot.save(reg));
        tag.putBoolean("Running", running);
        tag.putInt("TicksRemaining", ticksRemaining);
        tag.putInt("CycleDuration", cycleDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("DishSlot"))     dishSlot     = ItemStack.parseOptional(reg, tag.getCompound("DishSlot"));
        if (tag.contains("PressureSlot")) pressureSlot = ItemStack.parseOptional(reg, tag.getCompound("PressureSlot"));
        if (tag.contains("FuelSlot"))     fuelSlot     = ItemStack.parseOptional(reg, tag.getCompound("FuelSlot"));
        running        = tag.getBoolean("Running");
        ticksRemaining = tag.getInt("TicksRemaining");
        cycleDuration  = tag.getInt("CycleDuration");
    }
}
