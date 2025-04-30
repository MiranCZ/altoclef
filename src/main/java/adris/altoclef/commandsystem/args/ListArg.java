package adris.altoclef.commandsystem.args;

import adris.altoclef.commandsystem.exception.BadCommandSyntaxException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.StringReader;
import adris.altoclef.commandsystem.exception.CommandNotFinishedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class ListArg<T> extends Arg<List<T>> {


    private final Arg<T> argument;
    private final HashMap<String, List<T>> aliases = new HashMap<>();

    public ListArg(Arg<T> argument, String name) {
        super(name);
        this.argument = argument;
    }

    public ListArg(Arg<T> argument,String name, List<T> defaultValue) {
        super(name, defaultValue);
        this.argument = argument;
    }

    public ListArg(Arg<T> argument,String name, List<T> defaultValue, boolean showDefault) {
        super(name, defaultValue, showDefault);
        this.argument = argument;
    }



    public ListArg<T> addAlias(String alias, List<T> value) {
        aliases.put(alias, value);
        return this;
    }


    // FIXME doesnt work with nested list, but does anyone even need that
    protected static <T> List<T> parse(StringReader parser, StringParser<T> argumentFunc, HashMap<String, List<T>> aliases) throws CommandException {
        // not a list, single element only
        // need to peek to not consume any info for the child
        if (!parser.peek().startsWith("[")) {
            if (aliases.containsKey(parser.peek())) {
                return aliases.get(parser.next());
            } else {
                return List.of(argumentFunc.parse(parser));
            }
        }

        ParseResult p = getParts(parser);
        String[] parts = p.parts;
        boolean hasClosingBracket = p.hasClosingBracket;

        List<T> result = new ArrayList<>();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].strip();

            if (part.isEmpty()) {
                if (hasClosingBracket || i + 1 < parts.length) {
                    throw new BadCommandSyntaxException("Expected token");
                }
                throw new CommandNotFinishedException("Expected token");
            }

            StringReader partReader = new StringReader(part);
            if (aliases.containsKey(partReader.peek())) {
                result.addAll(aliases.get(partReader.next()));
            } else {
                result.add(argumentFunc.parse(partReader));
            }

            if (partReader.hasNext()) {
                throw new BadCommandSyntaxException("Unexpected token in list");
            }
        }

        if (!hasClosingBracket) {
            throw new CommandNotFinishedException("Expected ']'");
        }

        return result;
    }

    @Override
    public Stream<String> getSuggestions(StringReader reader) {
        try {
            StringReader copy = reader.copy();
            parse(copy, argument.getParser(), aliases);

            if (reader.hasNext() && reader.peek().startsWith(",")) {
                reader.nextChar();
            }
        } catch (BadCommandSyntaxException ignored) {
            return Stream.empty();
        } catch (CommandNotFinishedException ignored) {
            ParseResult parsed = getParts(reader);
            if (parsed.hasClosingBracket) {
                return Stream.empty();
            }
            String[] parts = parsed.parts;
            for (int i = 0; i < parts.length-1; i++) {
                if (parts[i].isEmpty()) {
                    return Stream.empty();
                }
            }

            if (parsed.endsInColumn) {
                return getArgSuggestions(new StringReader(""));
            }

            return getArgSuggestions(new StringReader(parts[parts.length-1]));
        } catch (CommandException e) {
            throw new IllegalStateException("Illegal type "+e.getClass().getSimpleName(), e);
        }

        // everything was consumed, nothing to return
        return getArgSuggestions(new StringReader(""));
    }

    private Stream<String> getArgSuggestions(StringReader reader) {
        return Stream.concat(argument.getSuggestions(reader), aliases.keySet().stream());
    }



    private static ParseResult getParts(StringReader reader) {
        String fullList = "";
        boolean hasClosingBracket = false;

        while (reader.hasNext()) {
            String line;
            try {
                line = reader.next();
            } catch (CommandException e) {
                throw new RuntimeException(e);
            }
            fullList += line + " ";

            if (line.endsWith("]")) {
                hasClosingBracket = true;
                break;
            }
        }
        fullList = fullList.strip();
        boolean endInColumn = fullList.endsWith(",");

        // remove [, ]
        if (fullList.startsWith("[")) {
            fullList = fullList.substring(1);
        }
        if (fullList.endsWith("]")) {
            fullList = fullList.substring(0, fullList.length()-1);
        }

        String[] parts = fullList.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].strip();
        }

        return new ParseResult(parts, hasClosingBracket, endInColumn);
    }

    private static record ParseResult(String[] parts, boolean hasClosingBracket, boolean endsInColumn) {
    }


    @Override
    protected StringParser<List<T>> getParser() {
        return reader -> parse(reader, argument.getParser(), aliases);
    }

    @Override
    public String getTypeName() {
        return argument.getTypeName() +" List";
    }

    @Override
    public Class<List<T>> getType() {
        //noinspection unchecked - this is not ideal, but cannot be done better
        return (Class<List<T>>)(Class<?>) List.class;
    }

}
