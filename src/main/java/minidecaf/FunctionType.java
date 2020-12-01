package minidecaf;

import java.util.List;

/**
 * 函数类型
 *
 * @author Namasikanam
 * @since 2020-09-11
 */
public class FunctionType {
    public final Type returnType;
    public final List<Type> parameterTypes;

    public FunctionType(Type returnType, List<Type> parameterTypes) {
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public boolean equals(FunctionType functionType) {
        if (!returnType.equals(functionType.returnType)) return false;
        if (parameterTypes.size() != functionType.parameterTypes.size()) return false;
        for (int i = 0; i < parameterTypes.size(); ++i)
            if (!parameterTypes.get(i).equals(functionType.parameterTypes.get(i)))
                return false;
        return true;
    }
}
