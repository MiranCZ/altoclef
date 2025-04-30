package adris.altoclef.commands.random;

import adris.altoclef.AltoClef;
import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.args.StringArg;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.lang.reflect.Field;

public class ScanCommand extends Command {

    public ScanCommand() throws CommandException {
        super("scan", "Locates nearest block",
                new StringArg("block", "DIRT")
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String blockStr = parser.get(String.class);

        Field[] declaredFields = Blocks.class.getDeclaredFields();
        Block block = null;

        for (Field field : declaredFields) {
            field.setAccessible(true);
            try {
                if (field.getName().equalsIgnoreCase(blockStr)) {
                    block = (Block) field.get(Blocks.class);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            field.setAccessible(false);
        }

        if (block == null) {
            mod.logWarning("Block named: " + blockStr + " not found :(");
            return;
        }

        BlockScanner blockScanner = mod.getBlockScanner();
        mod.log(blockScanner.getNearestBlock(block,mod.getPlayer().getPos())+"");
    }

}