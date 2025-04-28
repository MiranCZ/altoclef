package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.args.ItemTargetArg;
import adris.altoclef.commandsystem.args.ListArg;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.entity.GiveItemToPlayerTask;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveCommand extends Command {
    public GiveCommand() throws CommandException {
        super("give", "Collect an item and give it to you or someone else",
                new StringArg("username", null),
                new ListArg<>(new ItemTargetArg("items"), "items")
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        // Parse target username or fall back to current butler user
        String username = parser.get(String.class);
        if (username == null) {
            if (mod.getButler().hasCurrentUser()) {
                username = mod.getButler().getCurrentUser();
            } else {
                mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
                finish();
                return;
            }
        }
        // Parse list of requested items
        List<ItemTarget> requested = parser.get(List.class);
        if (requested == null || requested.isEmpty()) {
            mod.logWarning("No items specified to give.");
            finish();
            return;
        }

        // For each requested item, check if a matching item in inventory needs registration
        List<ItemTarget> resolved = new ArrayList<>();
        for (ItemTarget req : requested) {
            ItemTarget best = req;
            for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) {
                ItemStack stack = mod.getPlayer().getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    String invName = ItemHelper.stripItemName(stack.getItem());
                    if (invName.equalsIgnoreCase(req.getCatalogueName())) {
                        best = new ItemTarget(stack.getItem(), req.getTargetCount());
                        break;
                    }
                }
            }
            resolved.add(best);
        }

        // Submit a give task for each resolved item
        for (ItemTarget target : resolved) {
            mod.log(String.format("USER: %s : ITEM: %s x %d", username, target.getCatalogueName(), target.getTargetCount()));
            mod.runUserTask(new GiveItemToPlayerTask(username, target), this::finish);
        }
    }
}