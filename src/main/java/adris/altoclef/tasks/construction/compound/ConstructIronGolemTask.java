package adris.altoclef.tasks.construction.compound;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
//#if MC <= 11605
//$$ import net.minecraft.util.math.Direction;
//#endif

import java.util.Optional;

public class ConstructIronGolemTask extends Task {
    private BlockPos position;
    private boolean canBeFinished = false;

    public ConstructIronGolemTask() {

    }

    public ConstructIronGolemTask(BlockPos pos) {
        position = pos;
    }

    @Override
    protected void onStart() {
        AltoClef.getInstance().getBehaviour().push();
        AltoClef.getInstance().getBehaviour().addProtectedItems(Items.IRON_BLOCK, Items.CARVED_PUMPKIN);
        AltoClef.getInstance().getClientBaritoneSettings().blocksToAvoidBreaking.value.add(Blocks.IRON_BLOCK);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (!StorageHelper.itemTargetsMetInventory(golemMaterials(mod))) {
            setDebugState("Getting materials for the iron golem");
            return new CataloguedResourceTask(golemMaterials(mod));
        }
        if (position == null) {
            for (BlockPos pos : WorldHelper.scanRegion(
                    new BlockPos(mod.getPlayer().getBlockX(), 64, mod.getPlayer().getBlockZ()),
                    new BlockPos(mod.getPlayer().getBlockX(), 128, mod.getPlayer().getBlockZ()))) {
                if (mod.getWorld().getBlockState(pos).getBlock() == Blocks.AIR) {
                    position = pos;
                    break;
                }
            }
            if (position == null) {
                position = mod.getPlayer().getBlockPos();
            }
        }
        if (!WorldHelper.isBlock(position, Blocks.IRON_BLOCK)) {
            if (!WorldHelper.isBlock(position, Blocks.AIR)) {
                setDebugState("Destroying block in way of base iron block");
                return new DestroyBlockTask(position);
            }
            setDebugState("Placing the base iron block");
            return new PlaceBlockTask(position, Blocks.IRON_BLOCK);
        }
//        mod.getPlayer().getServer().getPlayerManager().getPlayer("camelCasedSnivy").getAdvancementTracker()
        if (!WorldHelper.isBlock(position.up(), Blocks.IRON_BLOCK)) {
            if (!WorldHelper.isBlock(position.up(), Blocks.AIR)) {
                setDebugState("Destroying block in way of center iron block");
                return new DestroyBlockTask(position.up());
            }
            setDebugState("Placing the center iron block");
            return new PlaceBlockTask(position.up(), Blocks.IRON_BLOCK);
        }
        if (!WorldHelper.isBlock(position.up().east(), Blocks.IRON_BLOCK)) {
            if (!WorldHelper.isBlock(position.up().east(), Blocks.AIR)) {
                setDebugState("Destroying block in way of east iron block");
                return new DestroyBlockTask(position.up().east());
            }
            setDebugState("Placing the east iron block");
            return new PlaceBlockTask(position.up().east(), Blocks.IRON_BLOCK);
        }
        if (!WorldHelper.isBlock(position.up().west(), Blocks.IRON_BLOCK)) {
            if (!WorldHelper.isBlock(position.up().west(), Blocks.AIR)) {
                setDebugState("Destroying block in way of west iron block");
                return new DestroyBlockTask(position.up().west());
            }
            setDebugState("Placing the west iron block");
            return new PlaceBlockTask(position.up().west(), Blocks.IRON_BLOCK);
        }
        if (!WorldHelper.isBlock(position.east(), Blocks.AIR)) {
            setDebugState("Clearing area on east side...");
            return new DestroyBlockTask(position.east());
        }
        if (!WorldHelper.isBlock(position.west(), Blocks.AIR)) {
            setDebugState("Clearing area on west side...");
            return new DestroyBlockTask(position.west());
        }
        if (!WorldHelper.isBlock(position.up(2), Blocks.AIR)) {
            setDebugState("Destroying block in way of pumpkin");
            return new DestroyBlockTask(position.up(2));
        }
        canBeFinished = true;
        setDebugState("Placing the pumpkin (I think)");
        return new PlaceBlockTask(position.up(2), Blocks.CARVED_PUMPKIN);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getClientBaritoneSettings().blocksToAvoidBreaking.value.remove(Blocks.IRON_BLOCK);
        AltoClef.getInstance().getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ConstructIronGolemTask;
    }

    @Override
    public boolean isFinished() {
        if (position == null) return false;
        Optional<Entity> closestIronGolem = AltoClef.getInstance().getEntityTracker().getClosestEntity(new Vec3d(position.getX(), position.getY(), position.getZ()), IronGolemEntity.class);
        return closestIronGolem.isPresent() && closestIronGolem.get().getBlockPos().isWithinDistance(position, 2) && canBeFinished;
    }

    @Override
    protected String toDebugString() {
        return "Construct Iron Golem";
    }

    private int ironBlocksNeeded(AltoClef mod) {
        if (position == null) {
            return 4;
        }
        int needed = 0;
        if (mod.getWorld().getBlockState(position).getBlock() != Blocks.IRON_BLOCK)
            needed++;
        if (mod.getWorld().getBlockState(position.up().west()).getBlock() != Blocks.IRON_BLOCK)
            needed++;
        if (mod.getWorld().getBlockState(position.up().east()).getBlock() != Blocks.IRON_BLOCK)
            needed++;
        if (mod.getWorld().getBlockState(position.up()).getBlock() != Blocks.IRON_BLOCK)
            needed++;
        return needed;
    }

    private ItemTarget[] golemMaterials(AltoClef mod) {
        if (position == null || mod.getWorld().getBlockState(position.up(2)).getBlock() != Blocks.CARVED_PUMPKIN)
            return new ItemTarget[]{
                    new ItemTarget(Items.IRON_BLOCK, ironBlocksNeeded(mod)),
                    new ItemTarget(Items.CARVED_PUMPKIN, 1)
            };
        else return new ItemTarget[]{
                new ItemTarget(Items.IRON_BLOCK, ironBlocksNeeded(mod))
        };
    }
}
