package minidecaf;

public class Symbol {
    public final String name;

    public final int offset;

    public final Type type;

    public Symbol(String name, int offset, Type type) {
        this.name = name;
        this.offset = offset;
        this.type = type;
    }

    @Override
    public String toString() {
        return name + "@" + type + ":" + offset;
    }
}
