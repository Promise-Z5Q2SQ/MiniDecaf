package minidecaf;

public abstract class Type {
    private final String name;

    Type(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    abstract public boolean equals(Type type); // 判断两个 Type 是否相等
}

class NoType extends Type {
    public NoType() {
        super("NoType");
    }

    @Override
    public boolean equals(Type type) {
        return type instanceof NoType;
    }
}

class IntType extends Type {
    public IntType() {
        super("IntType");
    }

    @Override
    public boolean equals(Type type) {
        return type instanceof IntType;
    }
}