package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

import java.util.List;

public class StopCommand extends Command {

    public StopCommand() {
        super(List.of("stop", "cancel"), "Stop task runner (stops all automation)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.getUserTaskChain().cancel(mod);
        finish();
    }
}
