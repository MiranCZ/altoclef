package adris.altoclef.newtaskcatalogue.gathertypes;

import adris.altoclef.AltoClef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * gather specific amount of item in a specific way (mining, crafting, killing) task is complete when the amount of the item in inventory is sufficient
 */
public abstract class GatherTask {

    private static int GLOBAL_ID = 0;
    private final List<GatherTask> children;
    private final ItemStack stack;
    private final int id;

    public GatherTask(ItemStack stack, List<GatherTask> children) {
        if (children == null) {
            children = new ArrayList<>();
        }
        if (stack == null) {
            stack = ItemStack.EMPTY;
        }
        this.children = children;
        this.stack = stack;
        this.id = GLOBAL_ID;
        GLOBAL_ID++;
    }

    private void updateChildren(AltoClef mod) {
        children.removeIf(child -> child.isComplete(mod));
    }

    public void addChild(GatherTask child) {
        if (child == null) return;
        if (child.getType() == GatherType.PARENT) {
            throw new IllegalStateException("Cannot add parent as a child!");
        }

        for (GatherTask task : children) {
            if (task.toString().equals(child.toString())) {
                throw new IllegalStateException("how tho wtf");
            }
        }
        children.add(child);
    }

    public List<GatherTask> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public ItemStack getItemStack() {
        return new ItemStack(stack.getItem(),stack.getCount());
    }

    // TODO implement
    public abstract List<ItemStack> getNeededItems();

    public abstract GatherType getType();

    public boolean isComplete(AltoClef mod) {
        updateChildren(mod);
        if (children.isEmpty()) {
            return this.isSelfComplete(mod);
        }

        return false;
    }

    protected abstract boolean isSelfComplete(AltoClef mod);


    @Override
    public int hashCode() {
        return id;
    }
}