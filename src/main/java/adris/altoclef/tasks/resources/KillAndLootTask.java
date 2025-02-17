package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.entity.Entity;

import java.util.function.Predicate;

public class KillAndLootTask extends ResourceTask {

    private final Class<?> toKill;

    private final Task killTask;

    public KillAndLootTask(Class<?> toKill, Predicate<Entity> shouldKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        this.toKill = toKill;
        killTask = new KillEntitiesTask(shouldKill, toKill);
    }

    public KillAndLootTask(Class<?> toKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        this.toKill = toKill;
        killTask = new KillEntitiesTask(toKill);
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (!mod.getEntityTracker().entityFound(toKill)) {
            if (isInWrongDimension(mod)) {
                setDebugState("Going to correct dimension.");
                return getToCorrectDimensionTask(mod);
            }
            setDebugState("Searching for mob...");
            return new TimeoutWanderTask();
        }
        // We found the mob!
        return killTask;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof KillAndLootTask task) {
            return task.toKill.equals(toKill);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect items from " + toKill.toGenericString();
    }
}
