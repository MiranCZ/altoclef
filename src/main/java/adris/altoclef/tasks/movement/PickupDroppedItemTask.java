package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.AbstractDoToClosestObjectTask;
import adris.altoclef.tasks.resources.SatisfyMiningRequirementTask;
import adris.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StlHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PickupDroppedItemTask extends AbstractDoToClosestObjectTask<ItemEntity> implements ITaskRequiresGrounded {
    private static final Task getPickaxeFirstTask = new SatisfyMiningRequirementTask(MiningRequirement.STONE);
    // Not clean practice, but it helps keep things self contained I think.
    private static boolean isGettingPickaxeFirstFlag = false;
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5, true);
    private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
    private final MovementProgressChecker progressChecker = new MovementProgressChecker();
    private final ItemTarget[] itemTargets;

    // This happens all the time in mineshafts and swamps/jungles
    private final Set<ItemEntity> blacklist = new HashSet<>();
    private final boolean freeInventoryIfFull;
    Block[] annoyingBlocks = new Block[]{
            Blocks.VINE,
            Blocks.NETHER_SPROUTS,
            Blocks.TWISTING_VINES,
            Blocks.TWISTING_VINES_PLANT,
            Blocks.WEEPING_VINES_PLANT,
            Blocks.LADDER,
            Blocks.TALL_GRASS,
            Blocks.GRASS
    };
    private Task unstuckTask = null;
    // Am starting to regret not making this a singleton
    private AltoClef mod;
    private boolean collectingPickaxeForThisResource = false;
    private ItemEntity currentDrop = null;

    public PickupDroppedItemTask(ItemTarget[] itemTargets, boolean freeInventoryIfFull) {
        this.itemTargets = itemTargets;
        this.freeInventoryIfFull = freeInventoryIfFull;
    }

    public PickupDroppedItemTask(ItemTarget target, boolean freeInventoryIfFull) {
        this(new ItemTarget[]{target}, freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount, boolean freeInventoryIfFull) {
        this(new ItemTarget(item, targetCount), freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount) {
        this(item, targetCount, true);
    }

    private static BlockPos[] generateSides(BlockPos pos) {
        return new BlockPos[]{
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,1,0,0),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,-1,0,0),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,0,0,1),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,0,0,-1),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,1,0,-1),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,1,0,1),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,-1,0,-1),
                adris.altoclef.multiversion.blockpos.BlockPosHelper.add(pos,-1,0,1)
        };
    }

    public static boolean isIsGettingPickaxeFirst(AltoClef mod) {
        return isGettingPickaxeFirstFlag && mod.getModSettings().shouldCollectPickaxeFirst();
    }

    private boolean isAnnoying(AltoClef mod, BlockPos pos) {
        if (annoyingBlocks != null) {
            for (Block AnnoyingBlocks : annoyingBlocks) {
                return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks ||
                        mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock ||
                        mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock ||
                        mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock ||
                        mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
            }
        }
        return false;
    }

    private BlockPos stuckInBlock(AltoClef mod) {
        BlockPos p = mod.getPlayer().getBlockPos();
        if (isAnnoying(mod, p)) return p;
        if (isAnnoying(mod, p.up())) return p.up();
        BlockPos[] toCheck = generateSides(p);
        for (BlockPos check : toCheck) {
            if (isAnnoying(mod, check)) {
                return check;
            }
        }
        BlockPos[] toCheckHigh = generateSides(p.up());
        for (BlockPos check : toCheckHigh) {
            if (isAnnoying(mod, check)) {
                return check;
            }
        }
        return null;
    }

    private Task getFenceUnstuckTask() {
        return new SafeRandomShimmyTask();
    }

    public boolean isCollectingPickaxeForThis() {
        return collectingPickaxeForThisResource;
    }

    @Override
    protected void onStart(AltoClef mod) {
        wanderTask.reset();
        progressChecker.reset();
        stuckCheck.reset();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (wanderTask.isActive() && !wanderTask.isFinished(mod)) {
            setDebugState("Wandering.");
            return wanderTask;
        }
        if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
            progressChecker.reset();
        }
        if (unstuckTask != null && unstuckTask.isActive() && !unstuckTask.isFinished(mod) && stuckInBlock(mod) != null) {
            setDebugState("Getting unstuck from block.");
            stuckCheck.reset();
            // Stop other tasks, we are JUST shimmying
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
            mod.getClientBaritone().getExploreProcess().onLostControl();
            return unstuckTask;
        }
        if (!progressChecker.check(mod) || !stuckCheck.check(mod)) {
            BlockPos blockStuck = stuckInBlock(mod);
            if (blockStuck != null) {
                unstuckTask = getFenceUnstuckTask();
                return unstuckTask;
            }
            stuckCheck.reset();
        }
        this.mod = mod;

        // If we're getting a pickaxe for THIS resource...
        if (isIsGettingPickaxeFirst(mod) && collectingPickaxeForThisResource && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
            progressChecker.reset();
            setDebugState("Collecting pickaxe first");
            return getPickaxeFirstTask;
        } else {
            if (StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
                isGettingPickaxeFirstFlag = false;
            }
            collectingPickaxeForThisResource = false;
        }

        if (!progressChecker.check(mod)) {
            mod.getClientBaritone().getPathingBehavior().forceCancel();
            if (currentDrop != null && !currentDrop.getStack().isEmpty()) {
                // We might want to get a pickaxe first.
                if (!isGettingPickaxeFirstFlag && mod.getModSettings().shouldCollectPickaxeFirst() && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
                    Debug.logMessage("Failed to pick up drop, will try to collect a stone pickaxe first and try again!");
                    collectingPickaxeForThisResource = true;
                    isGettingPickaxeFirstFlag = true;
                    return getPickaxeFirstTask;
                }
                Debug.logMessage(StlHelper.toString(blacklist, element -> element == null ? "(null)" : element.getStack().getItem().getTranslationKey()));
                Debug.logMessage("Failed to pick up drop, suggesting it's unreachable.");
                blacklist.add(currentDrop);
                mod.getEntityTracker().requestEntityUnreachable(currentDrop);
                return wanderTask;
            }
        }

        return super.onTick(mod);
    }


    @Override
    protected boolean isEqual(Task other) {
        // Same target items
        if (other instanceof PickupDroppedItemTask task) {
            return Arrays.equals(task.itemTargets, itemTargets) && task.freeInventoryIfFull == freeInventoryIfFull;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        StringBuilder result = new StringBuilder();
        result.append("Pickup Dropped Items: [");
        int c = 0;
        for (ItemTarget target : itemTargets) {
            result.append(target.toString());
            if (++c != itemTargets.length) {
                result.append(", ");
            }
        }
        result.append("]");
        return result.toString();
    }

    @Override
    protected Vec3d getPos(AltoClef mod, ItemEntity obj) {
        if (!obj.isOnGround() && !obj.isTouchingWater()) {
            // Assume we'll land down one or two blocks from here. We could do this more advanced but whatever.
            BlockPos p = obj.getBlockPos();
            if (!WorldHelper.isSolidBlock(mod, p.down(3))) {
                return obj.getPos().subtract(0, 2, 0);
            }
            return obj.getPos().subtract(0, 1, 0);
        }
        return obj.getPos();
    }

    @Override
    protected Optional<ItemEntity> getClosestTo(AltoClef mod, Vec3d pos) {
        return mod.getEntityTracker().getClosestItemDrop(
                pos,
                itemTargets);
    }

    @Override
    protected Vec3d getOriginPos(AltoClef mod) {
        return mod.getPlayer().getPos();
    }

    @Override
    protected Task getGoalTask(ItemEntity itemEntity) {
        if (!itemEntity.equals(currentDrop)) {
            currentDrop = itemEntity;
            progressChecker.reset();
            if (isGettingPickaxeFirstFlag && collectingPickaxeForThisResource) {
                Debug.logMessage("New goal, no longer collecting a pickaxe.");
                collectingPickaxeForThisResource = false;
                isGettingPickaxeFirstFlag = false;
            }
        }
        // Ensure our inventory is free if we're close
        boolean touching = mod.getEntityTracker().isCollidingWithPlayer(itemEntity);
        if (touching) {
            if (freeInventoryIfFull) {
                if (mod.getItemStorage().getSlotsThatCanFitInPlayerInventory(itemEntity.getStack(), false).isEmpty()) {
                    return new EnsureFreeInventorySlotTask();
                }
            }
        }
        return new GetToEntityTask(itemEntity);
    }

    @Override
    protected boolean isValid(AltoClef mod, ItemEntity obj) {
        return obj.isAlive() && !blacklist.contains(obj);
    }

}
