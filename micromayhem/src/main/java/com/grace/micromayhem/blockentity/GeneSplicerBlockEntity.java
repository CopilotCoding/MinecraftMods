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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Gene Splicer Block Entity.
 *
 * Slots (all chat-based, no GUI screen):
 *   Dish A — first parent strain (ready petri dish)
 *   Dish B — second parent strain (ready petri dish)
 *
 * Interaction:
 *   Right-click holding ready dish → loads into slot A, then B
 *   Right-click empty hand        → show status / strain summary
 *   Sneak + right-click           → start splice if both slots filled and types match
 *
 * Output:
 *   2–3 candidate recombinant dishes drop at the block.
 *   Each candidate has a × in the species name marking it as a recombinant.
 *   Chat shows a side-by-side comparison of all candidates vs both parents.
 */
public class GeneSplicerBlockEntity extends BlockEntity implements MenuProvider {

    private ItemStack dishA = ItemStack.EMPTY;
    private ItemStack dishB = ItemStack.EMPTY;

    private boolean running        = false;
    private int     ticksRemaining = 0;

    public GeneSplicerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENE_SPLICER.get(), pos, state);
    }

    // ---- Tick ----

    public static void tick(Level level, BlockPos pos, BlockState state, GeneSplicerBlockEntity be) {
        if (!be.running || !(level instanceof ServerLevel serverLevel)) return;
        be.ticksRemaining--;
        if (be.ticksRemaining <= 0) be.completeSplice(serverLevel, pos);
    }

    // ---- Interaction ----

    @Override
    public Component getDisplayName() { return Component.literal("Gene Splicer"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        handleInteraction(player);
        return null;
    }

    private void handleInteraction(Player player) {
        ItemStack held = player.getMainHandItem();

        if (running) {
            player.sendSystemMessage(Component.literal(
                "§2[Splicer] Splicing in progress — " + (ticksRemaining / 20) + "s remaining."));
            return;
        }

        // Load dish A or B
        if (held.getItem() instanceof PetriDishItem
                && PetriDishItem.getState(held) == PetriDishItem.DishState.READY) {

            if (!(player.level() instanceof ServerLevel sl)) return;
            MicrobeRegistry registry = MicrobeRegistry.get(sl);

            if (dishA.isEmpty()) {
                dishA = held.copy();
                held.shrink(1);
                MicrobeStrain s = getFirstStrain(dishA, registry);
                player.sendSystemMessage(Component.literal("§a[Splicer] Parent A loaded: §6"
                    + (s != null ? s.getScientificName() : "unknown")));
                setChanged(); return;
            }

            if (dishB.isEmpty()) {
                // Validate type compatibility before accepting
                MicrobeStrain a = getFirstStrain(dishA, registry);
                MicrobeStrain b = getFirstStrain(held, registry);
                String err = GeneticRecombination.validatePair(a, b);
                if (err != null) {
                    player.sendSystemMessage(Component.literal("§c[Splicer] " + err));
                    return;
                }
                dishB = held.copy();
                held.shrink(1);
                player.sendSystemMessage(Component.literal("§a[Splicer] Parent B loaded: §6"
                    + (b != null ? b.getScientificName() : "unknown")));
                player.sendSystemMessage(Component.literal(
                    "§7Sneak + right-click to begin splicing."));
                setChanged(); return;
            }

            player.sendSystemMessage(Component.literal(
                "§e[Splicer] Both slots filled. Sneak + right-click to start, or remove dishes first."));
            return;
        }

        // Empty hand — show status
        if (held.isEmpty()) showStatus(player);
    }

    private void showStatus(Player player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        MicrobeRegistry registry = MicrobeRegistry.get(sl);

        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
        player.sendSystemMessage(Component.literal("§2§lGENE SPLICER STATUS"));

        if (dishA.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Parent A: §c[empty]"));
        } else {
            MicrobeStrain s = getFirstStrain(dishA, registry);
            player.sendSystemMessage(Component.literal("§7Parent A: §6" +
                (s != null ? s.getScientificName() + " §7(" + s.type.displayName + ")" : "unknown")));
            if (s != null) printStrainSummary(player, s, "A");
        }

        if (dishB.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Parent B: §c[empty]"));
        } else {
            MicrobeStrain s = getFirstStrain(dishB, registry);
            player.sendSystemMessage(Component.literal("§7Parent B: §6" +
                (s != null ? s.getScientificName() + " §7(" + s.type.displayName + ")" : "unknown")));
            if (s != null) printStrainSummary(player, s, "B");
        }

        if (!dishA.isEmpty() && !dishB.isEmpty()) {
            MicrobeStrain a = getFirstStrain(dishA, registry);
            MicrobeStrain b = getFirstStrain(dishB, registry);
            String err = GeneticRecombination.validatePair(a, b);
            if (err != null) {
                player.sendSystemMessage(Component.literal("§c⚠ " + err));
            } else {
                int dur = GeneticRecombination.spliceDuration(a, b);
                player.sendSystemMessage(Component.literal(
                    "§aCompatible! Splice duration: §f" + (dur/20) + "s"));
                player.sendSystemMessage(Component.literal("§7Sneak + right-click to start."));
            }
        } else {
            player.sendSystemMessage(Component.literal("§7Load two ready Petri Dishes to splice."));
        }

        player.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
    }

    /** Called from sneak+right-click event in ModEvents. */
    public void startSplice(ServerPlayer player) {
        if (running) { player.sendSystemMessage(Component.literal("§e[Splicer] Already running.")); return; }
        if (dishA.isEmpty() || dishB.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Splicer] Both parent dishes must be loaded.")); return;
        }

        MicrobeRegistry registry = MicrobeRegistry.get((ServerLevel) player.level());
        MicrobeStrain a = getFirstStrain(dishA, registry);
        MicrobeStrain b = getFirstStrain(dishB, registry);
        String err = GeneticRecombination.validatePair(a, b);
        if (err != null) {
            player.sendSystemMessage(Component.literal("§c[Splicer] " + err)); return;
        }

        ticksRemaining = GeneticRecombination.spliceDuration(a, b);
        running = true;

        player.sendSystemMessage(Component.literal(
            "§2[Splicer] Splicing §6" + a.getScientificName() +
            " §2× §6" + b.getScientificName() +
            " §2— §f" + (ticksRemaining/20) + "s"));
        setChanged();
    }

    // ---- Complete splice ----

    private void completeSplice(ServerLevel level, BlockPos pos) {
        running = false;

        MicrobeRegistry registry = MicrobeRegistry.get(level);
        MicrobeStrain parentA = getFirstStrain(dishA, registry);
        MicrobeStrain parentB = getFirstStrain(dishB, registry);

        if (parentA == null || parentB == null) {
            dishA = dishB = ItemStack.EMPTY;
            setChanged(); return;
        }

        long seed = level.getSeed() ^ level.getGameTime();
        List<MicrobeStrain> candidates = GeneticRecombination.recombine(parentA, parentB, seed, registry);

        if (candidates.isEmpty()) {
            level.players().stream()
                .filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 100)
                .forEach(p -> p.sendSystemMessage(Component.literal(
                    "§c[Splicer] Recombination failed — type mismatch during processing.")));
            dishA = dishB = ItemStack.EMPTY;
            setChanged(); return;
        }

        // Drop candidate dishes
        double dx = pos.getX() + 0.5, dy = pos.getY() + 1.1, dz = pos.getZ() + 0.5;
        for (int i = 0; i < candidates.size(); i++) {
            MicrobeStrain child = candidates.get(i);
            ItemStack dish = new ItemStack(ModItems.PETRI_DISH.get());
            final long cid = child.strainId;
            NbtHelper.updateTag(dish, tag -> {
                tag.putString("DishState", PetriDishItem.DishState.READY.name());
                tag.putLongArray("StrainIds", new long[]{cid});
                tag.putBoolean("Mutated", false);
                tag.putBoolean("Recombinant", true);
            });
            double offsetX = (i - candidates.size() / 2.0) * 0.5;
            ItemEntity ie = new ItemEntity(level, dx + offsetX, dy, dz, dish);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }

        // Announce with full comparison table
        level.players().stream()
            .filter(p -> p.distanceToSqr(dx, dy, dz) < 100)
            .forEach(p -> {
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
                p.sendSystemMessage(Component.literal("§2§lSPLICE COMPLETE — " + candidates.size() + " recombinants"));
                p.sendSystemMessage(Component.literal(
                    "§7Parents: §6" + parentA.getScientificName() + " §7× §6" + parentB.getScientificName()));
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));

                // Parent summary
                p.sendSystemMessage(Component.literal("§7§lParent A:"));
                printStrainSummary(p, parentA, "A");
                p.sendSystemMessage(Component.literal("§7§lParent B:"));
                printStrainSummary(p, parentB, "B");
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
                p.sendSystemMessage(Component.literal("§7§lRecombinants:"));

                for (int i = 0; i < candidates.size(); i++) {
                    MicrobeStrain c = candidates.get(i);
                    p.sendSystemMessage(Component.literal(
                        "§2[" + (i+1) + "] §6" + c.getScientificName()));
                    printStrainSummary(p, c, null);
                    // Highlight traits that differ from both parents
                    highlightNovelTraits(p, c, parentA, parentB);
                    p.sendSystemMessage(Component.literal(" "));
                }

                p.sendSystemMessage(Component.literal("§7Pick up the recombinant you want."));
                p.sendSystemMessage(Component.literal("§8§m──────────────────────────────"));
            });

        dishA = dishB = ItemStack.EMPTY;
        setChanged();
    }

    // ---- Display helpers ----

    private static void printStrainSummary(Player p, MicrobeStrain s, @Nullable String label) {
        String prefix = label != null ? "§7  " : "§7  ";
        String effectColor = s.effect.beneficial() ? "§a" : (s.effect == MicrobeEffect.NONE ? "§e" : "§c");
        p.sendSystemMessage(Component.literal(
            prefix + "Effect: " + effectColor + s.effect.displayName +
            " §7| Vir: §f" + s.getVirulenceLabel() +
            " §7| Trans: §f" + s.getTransmissibilityLabel() +
            " §7| Gen." + s.generation));
        StringBuilder vecs = new StringBuilder(prefix + "Vectors: §f");
        for (int i = 0; i < s.spreadVectors.size(); i++) {
            vecs.append(s.spreadVectors.get(i).displayName);
            if (i < s.spreadVectors.size() - 1) vecs.append(", ");
        }
        p.sendSystemMessage(Component.literal(vecs.toString()));
    }

    private static void highlightNovelTraits(Player p, MicrobeStrain child,
                                              MicrobeStrain pa, MicrobeStrain pb) {
        // Effect novel?
        if (child.effect != pa.effect && child.effect != pb.effect) {
            p.sendSystemMessage(Component.literal(
                "  §d✦ Novel effect: " + child.effect.displayName + " (splice junction emergence)"));
        }
        // Vector novel?
        for (SpreadVector v : child.spreadVectors) {
            if (!pa.spreadVectors.contains(v) && !pb.spreadVectors.contains(v)) {
                p.sendSystemMessage(Component.literal(
                    "  §d✦ Novel vector: " + v.displayName + " (splice junction emergence)"));
            }
        }
        // Virulence higher than both parents?
        if (child.virulence > pa.virulence && child.virulence > pb.virulence) {
            p.sendSystemMessage(Component.literal("  §c✦ Hybrid vigour: virulence exceeds both parents!"));
        }
        // Transmissibility higher than both?
        if (child.transmissibility > pa.transmissibility + 0.05f
                && child.transmissibility > pb.transmissibility + 0.05f) {
            p.sendSystemMessage(Component.literal("  §c✦ Hybrid vigour: transmissibility exceeds both parents!"));
        }
    }

    // ---- Helpers ----

    private static MicrobeStrain getFirstStrain(ItemStack dish, MicrobeRegistry registry) {
        long[] ids = PetriDishItem.getStrainIds(dish);
        if (ids.length == 0) return null;
        return registry.getStrain(ids[0]);
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!dishA.isEmpty()) tag.put("DishA", dishA.save(reg));
        if (!dishB.isEmpty()) tag.put("DishB", dishB.save(reg));
        tag.putBoolean("Running", running);
        tag.putInt("TicksRemaining", ticksRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("DishA")) dishA = ItemStack.parseOptional(reg, tag.getCompound("DishA"));
        if (tag.contains("DishB")) dishB = ItemStack.parseOptional(reg, tag.getCompound("DishB"));
        running        = tag.getBoolean("Running");
        ticksRemaining = tag.getInt("TicksRemaining");
    }
}
