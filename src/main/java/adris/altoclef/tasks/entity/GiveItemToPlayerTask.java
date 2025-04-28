package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasks.movement.RunAwayFromPositionTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GiveItemToPlayerTask extends Task {

    private final String playerName;
    private final ItemTarget[] targets;

    private final CataloguedResourceTask resourceTask;
    private final List<ItemTarget> throwTarget = new ArrayList<>();
    private boolean droppingItems;

    private Task throwTask;

    public GiveItemToPlayerTask(String player, ItemTarget... targets) {
        playerName = player;
        this.targets = targets;
        resourceTask = TaskCatalogue.getSquashedItemTask(targets);
    }

    @Override
    protected void onStart() {
        droppingItems = false;
        throwTarget.clear();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (throwTask != null && throwTask.isActive() && !throwTask.isFinished()) {
            setDebugState("Throwing items");
            return throwTask;
        }

        Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(playerName);

        if (lastPos.isEmpty()) {
            setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
            return null;
        }
        Vec3d targetPos = lastPos.get().add(0, 0.2f, 0);

        if (droppingItems) {
            // THROW ITEMS
            setDebugState("Throwing items");
            LookHelper.lookAt(mod, targetPos);
            for (int i = 0; i < throwTarget.size(); ++i) {
                ItemTarget target = throwTarget.get(i);
                if (target.getTargetCount() > 0) {
                    Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                    if (has.isPresent()) {
                        Slot currentlyPresent = has.get();
                        if (Slot.isCursor(currentlyPresent)) {
                            ItemStack stack = StorageHelper.getItemStackInSlot(currentlyPresent);
                            // Update target
                            target = new ItemTarget(target, target.getTargetCount() - stack.getCount());
                            throwTarget.set(i, target);
                            Debug.logMessage("THROWING: " + has.get());
                            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
                        } else {
                            mod.getSlotHandler().clickSlot(currentlyPresent, 0, SlotActionType.PICKUP);
                        }
                        return null;
                    }
                }
            }

            boolean finishedThrowing = true;
            for (ItemTarget target : throwTarget) {
                if (target.getTargetCount() > 0) {
                    finishedThrowing = false;
                    break;
                }
            }
            if (finishedThrowing) {
                mod.log("Finished giving items.");
                stop();
                return null;
            }
            return new RunAwayFromPositionTask(6, WorldHelper.toBlockPos(targetPos));
        }

        if (!StorageHelper.itemTargetsMet(mod, targets)) {
            setDebugState("Collecting resources...");
            return resourceTask;
        }

        if (targetPos.isInRange(mod.getPlayer().getPos(), 1.5)) {
            if (!mod.getEntityTracker().isPlayerLoaded(playerName)) {
                mod.logWarning("Failed to get to player \"" + playerName + "\". We moved to where we last saw them but now have no idea where they are.");
                stop();
                return null;
            }
            droppingItems = true;
            throwTarget.addAll(Arrays.asList(targets));
        }

        setDebugState("Going to player...");
        return new FollowPlayerTask(playerName);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GiveItemToPlayerTask task) {
            if (!task.playerName.equals(playerName)) return false;
            return Arrays.equals(task.targets, targets);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Giving items to " + playerName;
    }
}
