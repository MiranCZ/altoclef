package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.GotoTarget;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
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
    private Vec3d targetPos;
    private boolean droppingItems;
    private boolean atGoal;
    private GotoTarget cords = null;

    private Task throwTask;

    public GiveItemToPlayerTask(String player, ItemTarget... targets) {
        playerName = player;
        this.targets = targets;
        resourceTask = TaskCatalogue.getSquashedItemTask(targets);
    }

    public GiveItemToPlayerTask(String player, GotoTarget gotocords, ItemTarget... targets) {
        playerName = player;
        this.targets = targets;
        this.cords = gotocords;
        resourceTask = TaskCatalogue.getSquashedItemTask(targets);
    }

    @Override
    protected void onStart() {
        droppingItems = false;
        atGoal = false;
        throwTarget.clear();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (throwTask != null && throwTask.isActive() && !throwTask.isFinished()) {
            setDebugState("Throwing items");
            return throwTask;
        }

        if (cords==null) {
            Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(playerName);
            if (lastPos.isEmpty()) {
                setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
                return null;
            }
            targetPos = lastPos.get().add(0, 1f, 0);
        }


        if (!StorageHelper.itemTargetsMet(mod, targets)) {
            setDebugState("Collecting resources...");
            return resourceTask;
        }

        if (cords!=null) {
            if (!atGoal) {
                atGoal = true;
                return new GetToBlockTask(new BlockPos(cords.getX(), cords.getY(), cords.getZ()), cords.getDimension());
            }
            Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(playerName);
            if (lastPos.isEmpty()) {
                setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
                return null;
            }
            targetPos = lastPos.get().add(0, 2f, 0);
        }

        if (droppingItems) {
            // For each target, pick up its stack and then throw it
            for (int i = 0; i < throwTarget.size(); i++) {
                ItemTarget target = throwTarget.get(i);
                if (target.getTargetCount() <= 0) continue;

                // Find a slot containing this item
                Optional<Slot> maybeSlot = mod.getItemStorage()
                        .getSlotsWithItemPlayerInventory(false, target.getMatches())
                        .stream()
                        .findFirst();

                if (maybeSlot.isEmpty()) continue;
                Slot slot = maybeSlot.get();

                // LOOK & DEBUG once per action
                setDebugState("Throwing items");
                LookHelper.lookAt(mod, targetPos);

                //Throws entire stack of items
                mod.getSlotHandler().clickSlot(slot, 1, SlotActionType.THROW);
            }
            mod.log("Finished giving items.");
            stop();
            return null;
        }

        if (targetPos.isInRange(mod.getPlayer().getPos(), 4)) {
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
