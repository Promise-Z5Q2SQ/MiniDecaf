package minidecaf;

// 值类别：左值或者右值
enum ValueKind {
    LVALUE,
    RVALUE
}

public abstract class Type {
    private final String name;
    public ValueKind valueKind;

    private Type(String name) {
        this.name = name;
        this.valueKind = ValueKind.RVALUE; // 默认右值
    }

    private Type(String name, ValueKind valueKind) {
        this.name = name;
        this.valueKind = valueKind;
    }

    @Override
    public String toString() {
        return name + ": " + valueKind;
    }

    abstract public Type referenced(); // 取地址

    abstract public Type dereferenced(); // 解引用

    abstract public Type valueCast(ValueKind targetValueKind); // 左右值转换

    abstract public boolean equals(Type type); // 判断两个 Type 是否相等

    abstract public int getSize(); // 类型所占内存空间的大小

    /**
     * 用于语句、声明等没有类型的分析树节点
     */
    public static class NoType extends Type {
        public NoType() {
            super("NoType");
        }

        @Override
        public boolean equals(Type type) {
            return type instanceof NoType;
        }

        @Override
        public int getSize() {
            throw new UnsupportedOperationException("Error: trying getting the size of NoType.");
        }

        @Override
        public Type referenced() {
            throw new UnsupportedOperationException("Error: trying referencing NoType.");
        }

        @Override
        public Type dereferenced() {
            throw new UnsupportedOperationException("Error: trying dereferencing NoType.");
        }

        // NoType 的左右值是无意义的
        @Override
        public Type valueCast(ValueKind targetValueCat) {
            return this;
        }
    }

    /**
     * 整型
     */
    public static class IntType extends Type {
        public IntType() {
            super("IntType");
        }

        public IntType(ValueKind valueKind) {
            super("IntType", valueKind);
        }

        @Override
        public boolean equals(Type type) {
            return (type instanceof IntType) && (valueKind == type.valueKind);
        }

        @Override
        public int getSize() {
            return 4;
        }

        @Override
        public Type referenced() {
            if (valueKind == ValueKind.LVALUE)
                return new PointerType(1);
            else
                throw new UnsupportedOperationException("Error: trying referencing an rvalue int");
        }

        @Override
        public Type dereferenced() {
            throw new UnsupportedOperationException("Error: trying dereferencing an int.");
        }

        @Override
        public Type valueCast(ValueKind targetValueKind) {
            return new IntType(targetValueKind);
        }
    }

    /**
     * 指针类型
     */
    public static class PointerType extends Type {
        public final int starNum;

        public PointerType(int starNum) {
            super("PointerType<" + starNum + ">");
            this.starNum = starNum;
        }

        public PointerType(int starNum, ValueKind valueCat) {
            super("PointerType<" + starNum + ">", valueCat);
            this.starNum = starNum;
        }

        @Override
        public boolean equals(Type type) {
            return (type instanceof PointerType) && (starNum == ((PointerType) type).starNum)
                    && (valueKind == type.valueKind);
        }

        @Override
        public int getSize() {
            return 4;
        }

        @Override
        public Type referenced() {
            if (valueKind == ValueKind.LVALUE)
                return new PointerType(starNum + 1);
            else
                throw new UnsupportedOperationException("Error: trying referencing an rvalue pointer");
        }

        @Override
        public Type dereferenced() {
            if (starNum > 1)
                return new PointerType(starNum - 1, ValueKind.LVALUE);
            else
                return new IntType(ValueKind.LVALUE);
        }

        @Override
        public Type valueCast(ValueKind targetValueKind) {
            return new PointerType(starNum, targetValueKind);
        }
    }
}