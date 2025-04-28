package adris.altoclef.commandsystem.args;

import adris.altoclef.commandsystem.exception.BadCommandSyntaxException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.StringReader;

import java.util.stream.Stream;

public class BoolArg extends Arg<Boolean> {

    public BoolArg(String name) {
        super(name);
    }

    public BoolArg(String name, Boolean defaultValue) {
        super(name, defaultValue);
    }

    public BoolArg(String name, Boolean defaultValue, boolean showDefault) {
        super(name, defaultValue, showDefault);
    }

    public static Boolean parse(StringReader parser) throws CommandException {
        String value = parser.next();
        try {
            return Boolean.parseBoolean(value);
        } catch (NumberFormatException e) {
            throw new BadCommandSyntaxException("Failed to parse '"+ value + "' into an Integer");
        }
    }

    @Override
    public Stream<String> getSuggestions(StringReader reader) {
        return Stream.empty();
    }

    @Override
    protected StringParser<Boolean> getParser() {
        return BoolArg::parse;
    }

    @Override
    public String getTypeName() {
        return "Integer";
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

}
