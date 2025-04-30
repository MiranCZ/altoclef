package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class CancelCommand extends Command {

    public CancelCommand() {
        super("cancel", "Cancel task runner (stops all automation)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.getUserTaskChain().cancel(mod);
        finish();
    }
}
