package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.baritone.GoalDodgeProjectiles;
import baritone.api.pathing.goals.Goal;

public class DodgeProjectilesTask extends CustomBaritoneGoalTask {

    private final double distanceHorizontal;
    private final double distanceVertical;

    public DodgeProjectilesTask(double distanceHorizontal, double distanceVertical) {
        this.distanceHorizontal = distanceHorizontal;
        this.distanceVertical = distanceVertical;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (cachedGoal != null) {
            // EntityTracker runs ensureUpdated automatically which calls updateState which locks the mutex,
            // so don't lock here.
            // Multithreading can be a hassle in more ways than one it seems.
            GoalDodgeProjectiles goal = (GoalDodgeProjectiles) cachedGoal;
        }
        return super.onTick(mod);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DodgeProjectilesTask task) {
            //if (task.mob.getPos().squaredDistanceTo(mob.getPos()) > 0.5) return false;
            if (Math.abs(task.distanceHorizontal - distanceHorizontal) > 1) return false;
            if (Math.abs(task.distanceVertical - distanceVertical) > 1) return false;
            return true;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Dodge arrows at " + distanceHorizontal + " blocks away";
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalDodgeProjectiles(mod, distanceHorizontal, distanceVertical);
    }
}
