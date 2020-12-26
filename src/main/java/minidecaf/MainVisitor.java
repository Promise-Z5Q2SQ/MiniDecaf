package minidecaf;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.*;

public final class MainVisitor extends MiniDecafBaseVisitor<Type> {
    private final StringBuilder stringBuilder; // 生成的目标汇编代码
    private boolean containsMain = false; // 标志是否有主函数
    private String currentFunction; // 当前函数
    private int localCount; // 局部变量计数
    private final Stack<Map<String, Symbol>> symbolTable = new Stack<>(); // 符号表
    private final Map<String, Type> declaredGlobalTable = new HashMap<>();
    private final Map<String, Type> initializedGlobalTable = new HashMap<>();
    private int condCount = 0; // 用于给条件语句和条件表达式所用的标签编号
    private int loopCount = 0; // 用于给循环语句所用的标签编号
    private final Stack<Integer> currentLoop = new Stack<>(); // 当前位置的循环标签编号
    private final Map<String, FunctionType> declaredFunctionTable = new HashMap<>(); // 已声明函数表
    private final Map<String, FunctionType> definedFunctionTable = new HashMap<>(); // 已定义函数表

    MainVisitor(StringBuilder stringBuilder) {
        this.stringBuilder = stringBuilder;
    }

    @Override
    public Type visitProgram(MiniDecafParser.ProgramContext ctx) {
        for (var child : ctx.children)
            visit(child);
        for (String global : declaredGlobalTable.keySet())
            if (initializedGlobalTable.get(global) == null) {
                stringBuilder.append("\t.comm "). // 未初始化的全局变量在 bss 段中
                        append(global).append(", ").append(declaredGlobalTable.get(global).getSize()).append(", 4\n"); // 对齐字节数为 4
            }
        if (!containsMain) reportError("no main function found", ctx);
        return new Type.NoType();
    }

    @Override
    public Type visitDefineFunction(MiniDecafParser.DefineFunctionContext ctx) {
        Type returnType = visit(ctx.type(0));
        currentFunction = ctx.IDENT(0).getText();
        if (declaredGlobalTable.get(currentFunction) != null)
            reportError("a global variable and a function have the same name", ctx);
        if (currentFunction.equals("main")) containsMain = true; // 出现主函数即记录
        stringBuilder.append("\t.text\n");// 表示以下内容在 text 段中
        stringBuilder.append("\t.global ").append(currentFunction).append("\n"); // 让该 label 对链接器可见
        stringBuilder.append(currentFunction).append(":\n");
        if (definedFunctionTable.get(currentFunction) != null)
            reportError("duplicate definition", ctx);
        List<Type> paramTypes = new ArrayList<>();
        for (int i = 1; i < ctx.type().size(); ++i)
            paramTypes.add(visit(ctx.type(i)));
        FunctionType functionType = new FunctionType(returnType, paramTypes);
        if (declaredFunctionTable.get(currentFunction) != null && !declaredFunctionTable.get(currentFunction).equals(functionType))
            reportError("the signature of the defined function is not the same as it is declared", ctx);
        declaredFunctionTable.put(currentFunction, functionType);
        definedFunctionTable.put(currentFunction, functionType);

        // construct prologue
        stackPush("ra");
        stackPush("fp");
        stringBuilder.append("\tmv fp, sp\n");
        int backtracePosition = stringBuilder.length();
        localCount = 0;
        symbolTable.add(new HashMap<>()); // 为函数开启新的作用域
        // 将函数的参数作为局部变量取出，这里参数的存储方式遵循 riscv gcc 的调用约定
        for (int i = 1; i < ctx.IDENT().size(); ++i) {
            String parameterName = ctx.IDENT().get(i).getText();
            if (symbolTable.peek().get(parameterName) != null)
                reportError("two parameters have the same name", ctx);
            if (i < 9) { // 前8个参数使用寄存器 a0-a7 储存
                localCount++;
                stringBuilder.append("\tsw a").append(i - 1).append(", ").append(-4 * i).append("(fp)\n");
                symbolTable.peek().put(parameterName, new Symbol(parameterName, -4 * i,
                        functionType.parameterTypes.get(i - 1).valueCast(ValueKind.LVALUE)));
            } else { // 剩余参数位于内存中，ra 前
                symbolTable.peek().put(parameterName, new Symbol(parameterName, 4 * (i - 9 + 2),
                        functionType.parameterTypes.get(i - 1).valueCast(ValueKind.LVALUE)));
            }
        }
        visit(ctx.compound_statement()); // 函数体
        symbolTable.pop(); // 删除函数作用域的符号表
        // 在没有返回语句的情况下，我们默认取 return 0
        stringBuilder.append("\tli t1, 0\n").append("\taddi sp, sp, -4\n").append("\tsw t1, 0(sp)\n");
        // 根据局部变量的数量，回填所需的栈空间
        stringBuilder.insert(backtracePosition, "\taddi sp, sp, " + (-4 * localCount) + "\n");
        // construct epilogue
        stringBuilder.append(".exit.").append(currentFunction).append(":\n\tlw a0, 0(sp)\n").append("\tmv sp, fp\n");
        stackPop("fp");
        stackPop("ra");
        stringBuilder.append("\tret\n\n");
        return new Type.NoType();
    }

    @Override
    public Type visitDeclareFunction(MiniDecafParser.DeclareFunctionContext ctx) {
        Type returnType = visit(ctx.type(0));
        String functionName = ctx.IDENT(0).getText();
        if (declaredGlobalTable.get(functionName) != null)
            reportError("a global variable and a function have the same name", ctx);
        List<Type> paramTypes = new ArrayList<>();
        for (int i = 1; i < ctx.type().size(); ++i)
            paramTypes.add(visit(ctx.type(i)));
        FunctionType functionType = new FunctionType(returnType, paramTypes);

        if (declaredFunctionTable.get(functionName) != null && !declaredFunctionTable.get(functionName).equals(functionType))
            reportError("declare a function with different parameters", ctx);
        declaredFunctionTable.put(functionName, functionType);
        return new Type.NoType();
    }

    @Override
    public Type visitType(MiniDecafParser.TypeContext ctx) {
        int starNum = ctx.children.size() - 1;
        if (starNum == 0) {
            if (!ctx.children.get(0).getText().equals("int")) reportError("class error", ctx);
            return new Type.IntType();
        } else
            return new Type.PointerType(starNum);
    }

    @Override
    public Type visitCompound_statement(MiniDecafParser.Compound_statementContext ctx) {
        for (var blockItem : ctx.blockitem())
            visit(blockItem);
        return new Type.NoType();
    }

    @Override
    public Type visitLocalIntOrPointerDecl(MiniDecafParser.LocalIntOrPointerDeclContext ctx) {
        Type type = visit(ctx.type());
        String name = ctx.IDENT().getText();
        if (symbolTable.peek().get(name) != null) // 若重复声明则报错
            reportError("try declaring a declared variable", ctx);
        symbolTable.peek().put(name, new Symbol(name, -4 * ++localCount, type.valueCast(ValueKind.LVALUE)));// 否则加入符号表
        var expr = ctx.expression();
        if (expr != null) {
            Type exprType = castToRValue(visit(expr), ctx);
            if (!exprType.equals(type))
                reportError("initialize value of type " + exprType + " to some variable of type " + type, ctx);
            stackPop("t0");
            stringBuilder.append("\tsw t0, ").append(-4 * localCount).append("(fp)\n");
        }
        return new Type.NoType();
    }

    @Override
    public Type visitLocalArrayDecl(MiniDecafParser.LocalArrayDeclContext ctx) {
        String arrayName = ctx.IDENT().getText();
        if (symbolTable.peek().get(arrayName) != null)
            reportError("duplicated array name", ctx);
        Deque<Type> types = new ArrayDeque<>(); // 逐层构建高维数组
        types.add(visit(ctx.type()).valueCast(ValueKind.LVALUE));
        for (int i = ctx.NUM().size() - 1; i >= 0; i--) {
            int x = Integer.parseInt(ctx.NUM(i).getText());
            if (x == 0) reportError("the dimension of array cannot be 0", ctx);
            types.addFirst(new Type.ArrayType(types.getFirst(), x)); // 多维数组的构建
        }
        assert types.getFirst() instanceof Type.ArrayType;
        Type type = types.getFirst();
        localCount += type.getSize() / 4; // 为数组每个元素预留空间
        symbolTable.peek().put(arrayName, new Symbol(arrayName, -4 * localCount, type));
        return new Type.NoType();
    }

    @Override
    public Type visitGlobalIntOrPointerDecl(MiniDecafParser.GlobalIntOrPointerDeclContext ctx) {
        // 全局变量可以多次声明，但只能被初始化一次。
        String name = ctx.IDENT().getText();
        if (declaredFunctionTable.get(name) != null)
            reportError("a global variable and a function have the same name", ctx);
        Type type = visit(ctx.type());
        if (declaredGlobalTable.get(name) != null && !declaredGlobalTable.get(name).equals(type))
            reportError("different global variables with same name are declared", ctx);
        declaredGlobalTable.put(name, type.valueCast(ValueKind.LVALUE));

        var num = ctx.NUM();
        if (num != null) {
            if (initializedGlobalTable.get(name) != null)
                reportError("initialize a global variable twice", ctx);
            initializedGlobalTable.put(name, type.valueCast(ValueKind.RVALUE));
            stringBuilder.append("\t.data\n") // 全局变量要放在 data 段中
                    .append("\t.align 4\n").append(name).append(":\n").append("\t.word ").append(num.getText()).append("\n");
        }
        return new Type.NoType();
    }

    @Override
    public Type visitGlobalArrayDecl(MiniDecafParser.GlobalArrayDeclContext ctx) {
        String name = ctx.IDENT().getText();
        if (declaredFunctionTable.get(name) != null)
            reportError("duplicated array name", ctx);
        // 与局部数组变量处理一致
        Deque<Type> types = new ArrayDeque<>(); // 逐层构建高维数组
        types.add(visit(ctx.type()).valueCast(ValueKind.LVALUE));
        for (int i = ctx.NUM().size() - 1; i >= 0; i--) {
            int x = Integer.parseInt(ctx.NUM(i).getText());
            if (x == 0) reportError("the dimension of array cannot be 0", ctx);
            types.addFirst(new Type.ArrayType(types.getFirst(), x)); // 多维数组的构建
        }
        Type type = types.getFirst();
        if (declaredGlobalTable.get(name) != null && !declaredGlobalTable.get(name).equals(type))
            reportError("different global array with same name are declared", ctx);
        declaredGlobalTable.put(name, type);
        return new Type.NoType();
    }

    @Override
    public Type visitReturnStatement(MiniDecafParser.ReturnStatementContext ctx) {
        Type returnType = castToRValue(visit(ctx.expression()), ctx);
        Type expectedType = definedFunctionTable.get(currentFunction).returnType;
        if (!expectedType.equals(returnType))
            reportError("return type " + returnType + " is inconsistent with expected return type " + expectedType, ctx);
        stringBuilder.append("\tj .exit.").append(currentFunction).append("\n");
        return new Type.NoType();
    }

    @Override
    public Type visitExpressionStatement(MiniDecafParser.ExpressionStatementContext ctx) {
        var expr = ctx.expression();
        if (expr != null) {
            visit(ctx.expression());
            stringBuilder.append("\taddi sp, sp, 4\n");
        }
        return new Type.NoType();
    }

    @Override
    public Type visitIfStatement(MiniDecafParser.IfStatementContext ctx) {
        int currentCondNo = condCount++;
        typeCheck(visit(ctx.expression()), Type.IntType.class, ctx);
        stackPop("t0");
        stringBuilder.append("\tbeqz t0, .else").append(currentCondNo).append("\n"); // 根据条件表达式的值判断是否要直接跳转至 else 分支
        visit(ctx.statement(0));
        stringBuilder.append("\tj .afterCondition").append(currentCondNo).append("\n"); // 在 then 分支结束后直接跳至分支语句末尾
        stringBuilder.append(".else").append(currentCondNo).append(":\n"); // 标记 else 分支开始部分的 label
        if (ctx.statement().size() > 1)
            visit(ctx.statement(1));
        stringBuilder.append(".afterCondition").append(currentCondNo).append(":\n");
        return new Type.NoType();
    }

    @Override
    public Type visitDefaultStatement(MiniDecafParser.DefaultStatementContext ctx) {
        symbolTable.add(new HashMap<>()); // 创建新的符号表
        visit(ctx.compound_statement());
        symbolTable.pop(); // 删除该作用域新的符号表
        return new Type.NoType();
    }

    @Override
    public Type visitForStatement(MiniDecafParser.ForStatementContext ctx) {
        int currentLoop = loopCount++;
        // for 循环里的表达式均可能为空，故这里先把各个表达式照出来
        MiniDecafParser.ExpressionContext init = null;
        MiniDecafParser.ExpressionContext ctrl = null;
        MiniDecafParser.ExpressionContext post = null;
        for (int i = 0; i < ctx.children.size(); ++i)
            if (ctx.children.get(i) instanceof MiniDecafParser.ExpressionContext) {
                if (ctx.children.get(i - 1).getText().equals("("))
                    init = (MiniDecafParser.ExpressionContext) (ctx.children.get(i));
                else if (ctx.children.get(i + 1).getText().equals(";"))
                    ctrl = (MiniDecafParser.ExpressionContext) (ctx.children.get(i));
                else
                    post = (MiniDecafParser.ExpressionContext) (ctx.children.get(i));
            }
        symbolTable.add(new HashMap<>()); // 开启一个新的作用域
        if (ctx.declaration() != null)
            visit(ctx.declaration());
        else if (init != null) {
            visit(init);
            stringBuilder.append("\taddi sp, sp, 4\n");
        }
        stringBuilder.append(".beforeLoop").append(currentLoop).append(":\n");
        if (ctrl != null) {
            typeCheck(visit(ctrl), Type.IntType.class, ctx);
            stringBuilder.append("\tlw t1, 0(sp)\n").append("\taddi sp, sp, 4\n").append("\tbeqz t1, .afterLoop").append(currentLoop).append("\n");
        }
        this.currentLoop.push(currentLoop);
        symbolTable.add(new HashMap<>()); // 开启一个新的作用域
        visit(ctx.statement()); // 访问循环体
        symbolTable.pop(); // 清空当前作用域符号表
        this.currentLoop.pop();
        stringBuilder.append(".continueLoop").append(currentLoop).append(":\n"); // continue 指令需要跳转到这里
        if (post != null) {
            visit(post);
            stringBuilder.append("\taddi sp, sp, 4\n");
        }
        symbolTable.pop(); // 清空当前作用域符号表
        stringBuilder.append("\tj .beforeLoop").append(currentLoop).append("\n")
                .append(".afterLoop").append(currentLoop).append(":\n");
        return new Type.NoType();
    }

    @Override
    public Type visitWhileStatement(MiniDecafParser.WhileStatementContext ctx) {
        int currentLoop = loopCount++;
        stringBuilder.append(".beforeLoop").append(currentLoop).append(":\n").
                append(".continueLoop").append(currentLoop).append(":\n"); // continue 指令需要跳转到这里
        typeCheck(visit(ctx.expression()), Type.IntType.class, ctx);
        stackPop("t0");
        stringBuilder.append("\tbeqz t0, .afterLoop").append(currentLoop).append("\n");
        this.currentLoop.push(currentLoop);
        visit(ctx.statement()); // 访问循环体
        this.currentLoop.pop();
        stringBuilder.append("\tj .beforeLoop").append(currentLoop).append("\n").
                append(".afterLoop").append(currentLoop).append(":\n");
        return new Type.NoType();
    }

    @Override
    public Type visitDoWhileStatement(MiniDecafParser.DoWhileStatementContext ctx) {
        int currentLoop = loopCount++;
        stringBuilder.append(".beforeLoop").append(currentLoop).append(":\n");
        this.currentLoop.push(currentLoop);
        visit(ctx.statement()); // 访问循环体
        this.currentLoop.pop();
        stringBuilder.append(".continueLoop").append(currentLoop).append(":\n"); // continue 指令需要跳转到这里
        typeCheck(visit(ctx.expression()), Type.IntType.class, ctx);
        stackPop("t0");
        stringBuilder.append("\tbnez t0, .beforeLoop").append(currentLoop).append("\n").
                append(".afterLoop").append(currentLoop).append(":\n");
        return new Type.NoType();
    }

    @Override
    public Type visitBreakStatement(MiniDecafParser.BreakStatementContext ctx) {
        if (currentLoop.isEmpty())
            reportError("break statement not within loop", ctx);
        stringBuilder.append("\tj .afterLoop").append(currentLoop.peek()).append("\n");
        return new Type.NoType();
    }

    @Override
    public Type visitContinueStatement(MiniDecafParser.ContinueStatementContext ctx) {
        if (currentLoop.isEmpty())
            reportError("continue statement not within loop", ctx);
        stringBuilder.append("\tj .continueLoop").append(currentLoop.peek()).append("\n");
        return new Type.NoType();
    }

    @Override
    public Type visitExpression(MiniDecafParser.ExpressionContext ctx) {
        return visit(ctx.assignment());
    }

    @Override
    public Type visitAssignment(MiniDecafParser.AssignmentContext ctx) {
        if (ctx.children.size() > 1) {
            Type unaryType = typeCheck(visit(ctx.unary()), Type.class, ValueKind.LVALUE, ctx);
            Type exprType = castToRValue(visit(ctx.expression()), ctx);
            if (!exprType.equals(unaryType.valueCast(ValueKind.RVALUE)))
                reportError("assign value of type " + exprType + " to some variable of type " + unaryType, ctx);
            stackPop("t1");
            stackPop("t0");
            stringBuilder.append("\tsw t1, 0(t0)\n");
            stackPush("t0");
            return unaryType;
//            String name = ctx.IDENT().getText();
//            Optional<Symbol> optionSymbol = lookupSymbol(name);
//            visit(ctx.expression());
//            if (optionSymbol.isPresent()) {
//                Symbol symbol = optionSymbol.get();
//                stackPop("t0");
//                stringBuilder.append("\tsw t0, ").append(symbol.offset).append("(fp)\n");
//                stackPush("t0");
//                return symbol.type;
//            } else if (declaredGlobalTable.get(name) != null) {
//                stackPop("t0");
//                stringBuilder.append("\tlui t1, %hi(").append(name).append(")\n"); // 读出全局变量地址的高 20 位
//                stringBuilder.append("\tsw t0, %lo(").append(name).append(")(t1)\n"); // 读出全局变量地址的低 12 位
//                stackPush("t0");
//                return declaredGlobalTable.get(name);
//            } else {
//                reportError("use variable that is not defined", ctx);
//                return new Type.NoType();
//            }
        } else {
            return visit(ctx.conditional());
        }
    }

    @Override
    public Type visitConditional(MiniDecafParser.ConditionalContext ctx) {
        if (ctx.children.size() > 1) {
            int currentCondNo = condCount++;
            typeCheck(visit(ctx.logical_or()), Type.IntType.class, ctx);
            stackPop("t0");
            stringBuilder.append("\tbeqz t0, .else").append(currentCondNo).append("\n"); // 根据条件表达式判断是否要跳转至 else 分支
            Type thenType = castToRValue(visit(ctx.expression()), ctx);
            stringBuilder.append("\tj .afterCondition").append(currentCondNo).append("\n"); // 在 then 分支结束后直接跳至分支语句末尾
            stringBuilder.append(".else").append(currentCondNo).append(":\n"); // 在 else 分支结束后直接跳至分支语句末尾
            Type elseType = castToRValue(visit(ctx.conditional()), ctx);
            stringBuilder.append(".afterCondition").append(currentCondNo).append(":\n");
            if (!thenType.equals(elseType))
                reportError("different types of branches of a ternary", ctx);
            return thenType;
        } else {
            return visit(ctx.logical_or());
        }
    }

    @Override
    public Type visitLogical_or(MiniDecafParser.Logical_orContext ctx) {
        if (ctx.children.size() > 1) {
            typeCheck(visit(ctx.logical_or()), Type.IntType.class, ctx);
            typeCheck(visit(ctx.logical_and()), Type.IntType.class, ctx);
            stackPop("t1");
            stackPop("t0");
            stringBuilder.append("\tsnez t0, t0\n").append("\tsnez t1, t1\n").append("\tor t0, t0, t1\n");
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.logical_and());
        }
    }

    @Override
    public Type visitLogical_and(MiniDecafParser.Logical_andContext ctx) {
        if (ctx.children.size() > 1) {
            typeCheck(visit(ctx.logical_and()), Type.IntType.class, ctx);
            typeCheck(visit(ctx.equality()), Type.IntType.class, ctx);
            stackPop("t1");
            stackPop("t0");
            stringBuilder.append("\tsnez t0, t0\n").append("\tsnez t1, t1\n").append("\tand t0, t0, t1\n");
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.equality());
        }
    }

    @Override
    public Type visitEquality(MiniDecafParser.EqualityContext ctx) {
        if (ctx.children.size() > 1) {
            Type leftType = castToRValue(visit(ctx.equality()), ctx);
            Type rightType = castToRValue(visit(ctx.relational()), ctx);
            if (!leftType.equals(rightType)) {
                reportError("the types of the both sides of \"==\"/\"!=\" must be same", ctx);
            }
            if (leftType instanceof Type.ArrayType || rightType instanceof Type.ArrayType) {
                reportError("array type cannot compare", ctx);
            }
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "==" -> stringBuilder.append("\tsub t0, t0, t1\n").append("\tseqz t0, t0\n");
                case "!=" -> stringBuilder.append("\tsub t0, t0, t1\n").append("\tsnez t0, t0\n");
            }
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.relational());
        }
    }

    @Override
    public Type visitRelational(MiniDecafParser.RelationalContext ctx) {
        if (ctx.children.size() > 1) {
            typeCheck(visit(ctx.relational()), Type.IntType.class, ctx);
            typeCheck(visit(ctx.additive()), Type.IntType.class, ctx);
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "<" -> stringBuilder.append("\tslt t0, t0, t1\n");
                case ">" -> stringBuilder.append("\tsgt t0, t0, t1\n").append("\tsnez t0, t0\n");
                case "<=" -> stringBuilder.append("\tsgt t0, t0, t1\n").append("\txori t0, t0, 1\n");
                case ">=" -> stringBuilder.append("\tslt t0, t0, t1\n").append("\txori t0, t0, 1\n");
            }
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.additive());
        }
    }

    @Override
    public Type visitAdditive(MiniDecafParser.AdditiveContext ctx) {
        if (ctx.children.size() > 1) {
            Type leftType = castToRValue(visit(ctx.additive()), ctx);
            Type rightType = castToRValue(visit(ctx.multiplicative()), ctx);
            // 将加法和减法的操作数存入寄存器
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "+" -> {
                    if (leftType instanceof Type.IntType && rightType instanceof Type.IntType) {
                        stringBuilder.append("\tadd t0, t0, t1\n");
                        stackPush("t0");
                        return new Type.IntType();
                    } else if (leftType instanceof Type.PointerType && rightType instanceof Type.IntType) {
                        stringBuilder.append("\tslli t1, t1, 2\n").append("\tadd t0, t0, t1\n");
                        stackPush("t0");
                        return leftType;
                    } else if (leftType instanceof Type.IntType && rightType instanceof Type.PointerType) {
                        stringBuilder.append("\tslli t0, t0, 2\n").append("\tadd t0, t0, t1\n");
                        stackPush("t0");
                        return rightType;
                    } else {
                        reportError("only the followings are legal for addition operation: 1. pointer + integer 2. integer + pointer 3. integer + integer", ctx);
                        return new Type.NoType();
                    }
                }
                case "-" -> {
                    if (leftType instanceof Type.IntType && rightType instanceof Type.IntType) {
                        stringBuilder.append("\tsub t0, t0, t1\n");
                        stackPush("t0");
                        return new Type.IntType();
                    } else if (leftType instanceof Type.PointerType && rightType instanceof Type.IntType) {
                        stringBuilder.append("\tslli t1, t1, 2\n").append("\tsub t0, t0, t1\n");
                        stackPush("t0");
                        return leftType;
                    } else if (leftType instanceof Type.PointerType && rightType.equals(leftType)) {
                        stringBuilder.append("\tsub t0, t0, t1\n").append("\tsrai t0, t0, 2\n");
                        stackPush("t0");
                        return new Type.IntType();
                    } else {
                        reportError("only the followings are legal for subtraction operation: 1. pointer - integer 2. integer - integer", ctx);
                        return new Type.NoType();
                    }
                }
            }
            // 将运算结果存回栈中
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.multiplicative());
        }
    }

    @Override
    public Type visitMultiplicative(MiniDecafParser.MultiplicativeContext ctx) {
        // 与加减操作基本相同
        if (ctx.children.size() > 1) {
            typeCheck(visit(ctx.multiplicative()), Type.IntType.class, ctx);
            typeCheck(visit(ctx.unary()), Type.IntType.class, ctx);
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "*" -> stringBuilder.append("\tmul t0, t0, t1\n");
                case "/" -> stringBuilder.append("\tdiv t0, t0, t1\n");
                case "%" -> stringBuilder.append("\trem t0, t0, t1\n");
            }
            stackPush("t0");
            return new Type.IntType();
        } else {
            return visit(ctx.unary());
        }
    }

    @Override
    public Type visitOperatorUnary(MiniDecafParser.OperatorUnaryContext ctx) {
        Type type = visit(ctx.unary()); //递归循环
        String op = ctx.children.get(0).getText();
        if (op.equals("*")) {
            return castToRValue(type, ctx).dereferenced();
        }
        if (op.equals("&")) {
            return type.referenced();
        } else {
            typeCheck(type, Type.IntType.class, ctx);
            stackPop("t0");
            switch (op) {
                case "-" -> stringBuilder.append("\tneg t0, t0\n");
                case "~" -> stringBuilder.append("\tnot t0, t0\n");
                case "!" -> stringBuilder.append("\tseqz t0, t0\n");
            }
            stackPush("t0");
            return new Type.IntType();
        }
    }

    @Override
    public Type visitCastUnary(MiniDecafParser.CastUnaryContext ctx) {
        Type srcType = visit(ctx.unary());
        Type dstType = visit(ctx.type());
        return dstType.valueCast(srcType.valueKind);
    }

    @Override
    public Type visitPostfixUnary(MiniDecafParser.PostfixUnaryContext ctx) {
        return visit(ctx.postfix());
    }

    @Override
    public Type visitFunctionPostfix(MiniDecafParser.FunctionPostfixContext ctx) {
        String functionName = ctx.IDENT().getText();
        if (declaredFunctionTable.get(functionName) == null)
            reportError("call undeclared function", ctx);
        FunctionType functionType = declaredFunctionTable.get(functionName);
        if (functionType.parameterTypes.size() != ctx.expression().size())
            reportError("parameters matching error", ctx);
        // 这里参数的调用方式遵循 riscv gcc 的调用约定
        for (int i = ctx.expression().size() - 1; i >= 0; i--) {
            Type type = castToRValue(visit(ctx.expression().get(i)), ctx);
            if (!type.equals(functionType.parameterTypes.get(i)))
                reportError("the type of argument " + i + " is different from the type of parameter " + i + " of function " + functionName, ctx);
            if (i < 8) stackPop("a" + i); // 前8个参数使用寄存器 a0-a7 传递，其余直接存在内存中
        }
        stringBuilder.append("\tcall ").append(functionName).append("\n"); // 调用函数
        stackPush("a0"); // 函数的返回值存储在a0中
        return functionType.returnType;
    }

    @Override
    public Type visitPrimaryPostfix(MiniDecafParser.PrimaryPostfixContext ctx) {
        return visit(ctx.primary());
    }

    @Override
    public Type visitArrayPostfix(MiniDecafParser.ArrayPostfixContext ctx) {
        Type postfixType = castToRValue(visit(ctx.postfix()), ctx);
        typeCheck(visit(ctx.expression()), Type.IntType.class, ValueKind.RVALUE, ctx);
        stackPop("t1");
        stackPop("t0");
        // 下标运算符只能操作指针或数组
        if (postfixType instanceof Type.PointerType) {
            stringBuilder.append("\tslli t1, t1, 2\n").
                    append("\tadd t0, t0, t1\n");
            stackPush("t0");
            return postfixType.dereferenced();
        } else if (postfixType instanceof Type.ArrayType) {
            Type baseType = ((Type.ArrayType) postfixType).baseType;
            stringBuilder.append("\tli t2, ").append(baseType.getSize()).append("\n").
                    append("\tmul t1, t1, t2\n").
                    append("\tadd t0, t0, t1\n");
            stackPush("t0");
            return baseType;
        } else {
            reportError("the subscript operator could only be applied to a pointer or an array", ctx);
            return new Type.NoType();
        }
    }

    @Override
    public Type visitNumberPrimary(MiniDecafParser.NumberPrimaryContext ctx) {
        TerminalNode num = ctx.NUM();
        BigInteger bigInteger = new BigInteger(num.getText());
        BigInteger maxInteger = new BigInteger(String.valueOf(Integer.MAX_VALUE));
        // 检验数字字面量不能超过整型的最大值
        if (maxInteger.compareTo(bigInteger) <= 0)
            reportError("too large number", ctx);
        stringBuilder.append("\tli t0, ").append(num.getText()).append("\n");
        stackPush("t0");
        return new Type.IntType();
    }

    @Override
    public Type visitParenthesizedPrimary(MiniDecafParser.ParenthesizedPrimaryContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Type visitIdentPrimary(MiniDecafParser.IdentPrimaryContext ctx) {
        String name = ctx.IDENT().getText();
        Optional<Symbol> optionSymbol = lookupSymbol(name);
        if (optionSymbol.isPresent()) {
            Symbol symbol = optionSymbol.get();
            stringBuilder.append("\taddi t0, fp, ").append(symbol.offset).append("\n");
            stackPush("t0");
            return symbol.type;
        } else if (declaredGlobalTable.get(name) != null) { // 全局变量
            stringBuilder.append("\tlui t0, %hi(").append(name).append(")\n") // 读出全局变量地址的高 20 位
                    .append("\taddi t0, t0, %lo(").append(name).append(")\n"); // 读出全局变量地址的低 12 位
            stackPush("t0");
            return declaredGlobalTable.get(name);
        } else {
            reportError("use variable that is not defined", ctx);
            return new Type.IntType();
        }
    }

    /**
     * 报错，并输出错误信息和错误位置。
     *
     * @param s   错误信息
     * @param ctx 发生错误的环境，用于确定错误的位置
     */
    private void reportError(String s, ParserRuleContext ctx) {
        throw new RuntimeException("Error("
                + ctx.getStart().getLine() + ", "
                + ctx.getStart().getCharPositionInLine() + "): " + s + ".\n");
    }

    /**
     * 将寄存器的值压入栈中。
     *
     * @param reg 待压栈的寄存器
     */
    private void stackPush(String reg) {
        stringBuilder.append("\taddi sp, sp, -4\n");
        stringBuilder.append("\tsw ").append(reg).append(", 0(sp)\n");
    }

    /**
     * 将栈顶的值弹出到寄存器中。
     *
     * @param reg 用于存储栈顶值的寄存器
     */
    private void stackPop(String reg) {
        stringBuilder.append("\tlw ").append(reg).append(", 0(sp)\n");
        stringBuilder.append("\taddi sp, sp, 4\n");
    }

    /**
     * 优先从内层开始查询符号表
     *
     * @param v 被查询的变量名
     */
    private Optional<Symbol> lookupSymbol(String v) {
        for (int i = symbolTable.size() - 1; i >= 0; --i) {
            var map = symbolTable.elementAt(i);
            if (map.containsKey(v))
                return Optional.of(map.get(v));
        }
        return Optional.empty();
    }

    /**
     * 类型和值类别的转换，转换时检查是否可行。
     *
     * @param actualType     转换前的实际类型
     * @param expectedType   期望被转换到的类型
     * @param neededValueCat 所需的值类别
     * @return 转换后的结果类型
     */
    private Type typeCheck(Type actualType, Class<?> expectedType, ValueKind neededValueCat, ParserRuleContext ctx) {
        if (!expectedType.isAssignableFrom(actualType.getClass()))
            reportError("type " + actualType + " appears, but " + expectedType.getName() + " is expected", ctx);
        if (neededValueCat == ValueKind.LVALUE && actualType.valueKind == ValueKind.RVALUE)
            reportError("an lvalue is needed here", ctx);
        if (neededValueCat == ValueKind.RVALUE && actualType.valueKind == ValueKind.LVALUE) {
            stackPop("t0");
            stringBuilder.append("\tlw t0, 0(t0)\n");
            stackPush("t0");
            return actualType.valueCast(ValueKind.RVALUE);
        }
        return actualType.valueCast(neededValueCat);
    }

    // 缺省值类别，默认为右值
    private Type typeCheck(Type actualType, Class<?> expectedType, ParserRuleContext ctx) {
        return typeCheck(actualType, expectedType, ValueKind.RVALUE, ctx);
    }

    // 不作类型转换，仅仅要求右值
    private Type castToRValue(Type actualType, ParserRuleContext ctx) {
        return typeCheck(actualType, Type.class, ValueKind.RVALUE, ctx);
    }
}