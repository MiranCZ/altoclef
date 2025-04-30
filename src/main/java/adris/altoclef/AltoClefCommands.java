package adris.altoclef;

import adris.altoclef.commands.*;
import adris.altoclef.commands.random.ScanCommand;
import adris.altoclef.commands.random.DummyTaskCommand;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * Initializes altoclef's built in commands.
 */
public class AltoClefCommands {

    public static void init() throws CommandException {
        // List commands here
        AltoClef.getCommandExecutor().registerNewCommand(
                new HelpCommand(),
                new GetCommand(),
                new ListCommand(),
                new EquipCommand(),
                new DepositCommand(),
                new StashCommand(),
                new GotoCommand(),
                new IdleCommand(),
                new HeroCommand(),
                new CoordsCommand(),
                new StatusCommand(),
                new InventoryCommand(),
                new LocateStructureCommand(),
                new StopCommand(),
                new CancelCommand(),
                new PauseCommand(),
                new UnPauseCommand(),
                new SetGammaCommand(),
                new TestCommand(),
                new FoodCommand(),
                new MeatCommand(),
                new ReloadSettingsCommand(),
                new GamerCommand(),
                new MarvionCommand(),
                new DummyTaskCommand(),
                new FollowCommand(),
                new ScanCommand()
        );
    }
}
