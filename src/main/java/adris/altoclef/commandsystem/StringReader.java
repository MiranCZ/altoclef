package adris.altoclef.commandsystem;

import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.CommandNotFinishedException;

import java.util.ArrayList;
import java.util.List;

public class StringReader {


    private final List<String> parts;
    private final List<Integer> indexStarts;

    public StringReader(String line){
        parts = new ArrayList<>();
        indexStarts = new ArrayList<>();

        String[] tokens = line.split(" ", -1);

        int from = 0;
        for (String tok : tokens) {
            int start = line.indexOf(tok, from);
            parts.add(tok);
            indexStarts.add(start);

            from = start + tok.length() + 1;
        }

        indexStarts.add(line.length());
    }

    private StringReader(List<String> parts, List<Integer> indexStarts) {
        this.parts = parts;
        this.indexStarts = indexStarts;
    }

    public String next() throws CommandException {
        if (parts.isEmpty()) {
            throw new CommandNotFinishedException("String was fully consumed!");
        }

        indexStarts.removeFirst();
        return parts.removeFirst();
    }

    public String nextOrEmpty() {
        if (parts.isEmpty()) return "";

        try {
            return next();
        } catch (CommandException e) {
            throw new IllegalStateException();
        }
    }

    public int size() {
        return parts.size();
    }


    public String peek()  throws CommandException {
        if (parts.isEmpty()) {
            throw new CommandNotFinishedException("String was fully consumed!");
        }

        return parts.getFirst();
    }

    public Character nextChar() throws CommandException {
        if (parts.isEmpty()) {
            throw new CommandNotFinishedException("String was fully consumed!");
        }

        String part = parts.getFirst();

        // consume rest of the string
        if (part.length() == 1) {
            indexStarts.removeFirst();
            return parts.removeFirst().charAt(0);
        }

        // consume the char
        parts.set(0, part.substring(1));

        return part.charAt(0);
    }

    public boolean hasNext() {
        return !parts.isEmpty();
    }

    public int getIndex() {
        return indexStarts.getFirst();
    }

    public StringReader copy() {
        return new StringReader(new ArrayList<>(this.parts), new ArrayList<>(indexStarts));
    }

    public void set(StringReader other) {
        parts.clear();
        parts.addAll(other.parts);

        indexStarts.clear();
        indexStarts.addAll(other.indexStarts);
    }

}
