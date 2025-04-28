package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.*;
import adris.altoclef.commandsystem.args.CataloguedItemArg;
import adris.altoclef.commandsystem.args.ListArg;
import adris.altoclef.commandsystem.exception.BadCommandSyntaxException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class EquipCommand extends Command {

    public EquipCommand() {
        super("equip", "Equips items",
                new ListArg<>(new EquipmentItemArg("equipment"), "[equippable_items]")
                        .addAlias("leather", Arrays.stream(ItemHelper.LEATHER_ARMORS).map(Item::toString).toList())
                        .addAlias("iron",Arrays.stream(ItemHelper.GOLDEN_ARMORS).map(Item::toString).toList())
                        .addAlias("gold", Arrays.stream(ItemHelper.IRON_ARMORS).map(Item::toString).toList())
                        .addAlias("diamond", Arrays.stream(ItemHelper.DIAMOND_ARMORS).map(Item::toString).toList())
                        .addAlias("netherite", Arrays.stream(ItemHelper.NETHERITE_ARMORS).map(Item::toString).toList())
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        List<ItemTarget> items = parser.get(List.class);

        for (ItemTarget target : items) {
            for (Item item : target.getMatches()) {
                if (!(item instanceof Equipment)) {
                    throw new RuntimeCommandException("'"+item.toString().toUpperCase() + "' cannot be equipped!");
                }
            }
        }

        mod.runUserTask(new EquipArmorTask(items.toArray(new ItemTarget[0])), this::finish);
    }


    // this is kinda meh way to do it
    private static class EquipmentItemArg extends CataloguedItemArg {

        public EquipmentItemArg(String name) {
            super(name);
        }

        @Override
        protected StringParser<String> getParser() {
            return this::parseLocal;
        }

        private String parseLocal(StringReader reader) throws CommandException {
            StringParser<String> parentParser = super.getParser();

            ParseResult result = getSupplied(reader.copy(), parentParser);
            if (result == ParseResult.NOT_FINISHED) {
                String first = reader.peek();
                if (getSuggestions(null).noneMatch(s -> s.startsWith(first))) {
                    throw new BadCommandSyntaxException("Not equipment named '"+first+"' exists");
                }
            }

            String parsed = parentParser.parse(reader);

            if (!isEquipment(parsed)) {
                throw new BadCommandSyntaxException("Item '"+parsed+"' is not an equipment");
            }

            return parsed;
        }

        @Override
        public Stream<String> getSuggestions(StringReader reader) {
            return super.getSuggestions(reader).filter(EquipmentItemArg::isEquipment);
        }

        private static boolean isEquipment(String cataloguedItem) {
            return Arrays.stream(new ItemTarget(cataloguedItem).getMatches()).anyMatch(i -> i instanceof Equipment);
        }
    }


}
