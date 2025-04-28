package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class PauseCommand extends Command {
    public PauseCommand() {
        super("pause", "Pauses the bot after the task thats running (Still in development!)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        if (!mod.getUserTaskChain().isActive()) {
            mod.log("Bot has no current task");
            finish();
        } else {
            mod.setStoredTask(mod.getUserTaskChain().getCurrentTask());
            mod.setPaused(true);
            mod.getUserTaskChain().stop();
            mod.log("Pausing Bot and time");
            finish();
        }
    }
}