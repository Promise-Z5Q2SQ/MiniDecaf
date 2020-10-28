package minidecaf;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MainVisitor extends MiniDecafBaseVisitor<Type> {
    private final StringBuilder stringBuilder; // 生成的目标汇编代码
    private boolean containsMain = false; // 标志是否有主函数
    private String currentFunction; // 当前函数
    private int localCount; // 局部变量计数
    private Map<String, Symbol> symbolTable = new HashMap<>(); // 符号表
    private int condNo = 0; // 用于给条件语句和条件表达式所用的标签编号

    MainVisitor(StringBuilder stringBuilder) {
        this.stringBuilder = stringBuilder;
    }

    @Override
    public Type visitProgram(MiniDecafParser.ProgramContext ctx) {
        visit(ctx.function());
        if (!containsMain) reportError("no main function", ctx);
        return null;
    }

    @Override
    public Type visitFunction(MiniDecafParser.FunctionContext ctx) {
        visit(ctx.type());
        currentFunction = ctx.IDENT().getText();
        if (currentFunction.equals("main")) containsMain = true; // 出现主函数即记录
        stringBuilder.append("\t.text\n");// 表示以下内容在 text 段中
        stringBuilder.append("\t.global ").append(currentFunction).append("\n"); // 让该 label 对链接器可见
        stringBuilder.append(currentFunction).append(":\n");
        // construct prologue
        stackPush("ra");
        stackPush("fp");
        stringBuilder.append("\tmv fp, sp\n");
        int backtracePosition = stringBuilder.length();
        localCount = 0;
        for (var statement : ctx.blockitem())
            visit(statement);
        // 在没有返回语句的情况下，我们默认取 return 0
        stringBuilder.append("\tli t1, 0\n").append("\taddi sp, sp, -4\n").append("\tsw t1, 0(sp)\n");
        // 根据局部变量的数量，回填所需的栈空间
        stringBuilder.insert(backtracePosition, "\taddi sp, sp, " + (-4 * localCount) + "\n");
        // construct epilogue
        stringBuilder.append(".exit.").append(currentFunction).append(":\n\tlw a0, 0(sp)\n").append("\tmv sp, fp\n");
        stackPop("fp");
        stackPop("ra");
        stringBuilder.append("\tret\n\n");
        return null;
    }

    @Override
    public Type visitType(MiniDecafParser.TypeContext ctx) {
        if (!ctx.getText().equals("int")) reportError("class error", ctx);
        return null;
    }

    @Override
    public Type visitDeclaration(MiniDecafParser.DeclarationContext ctx) {
        visit(ctx.type());
        String name = ctx.IDENT().getText();
        if (symbolTable.get(name) != null) // 若重复声明则报错
            reportError("try declaring a declared variable", ctx);
        symbolTable.put(name, new Symbol(name, -4 * ++localCount, new Type.IntType()));// 否则加入符号表
        var expr = ctx.expression();
        if (expr != null) {
            visit(expr);
            stackPop("t0");
            stringBuilder.append("\tsw t0, ").append(-4 * localCount).append("(fp)\n");
        }
        return null;
    }

    @Override
    public Type visitReturnStatement(MiniDecafParser.ReturnStatementContext ctx) {
        visit(ctx.expression());
        stringBuilder.append("\tj .exit.").append(currentFunction).append("\n");
        return null;
    }

    @Override
    public Type visitExpressionStatement(MiniDecafParser.ExpressionStatementContext ctx) {
        var expr = ctx.expression();
        if (expr != null) {
            visit(ctx.expression());
            stackPop("t0");
        }
        return null;
    }

    @Override
    public Type visitIfStatement(MiniDecafParser.IfStatementContext ctx) {
        int currentCondNo = condNo++;
        visit(ctx.expression());
        stackPop("t0");
        stringBuilder.append("\tbeqz t0, .else").append(currentCondNo).append("\n"); // 根据条件表达式的值判断是否要直接跳转至 else 分支
        visit(ctx.statement(0));
        stringBuilder.append("\tj .afterCondition").append(currentCondNo).append("\n"); // 在 then 分支结束后直接跳至分支语句末尾
        stringBuilder.append(".else").append(currentCondNo).append(":\n"); // 标记 else 分支开始部分的 label
        if (ctx.statement().size() > 1)
            visit(ctx.statement(1));
        stringBuilder.append(".afterCondition").append(currentCondNo).append(":\n");
        return null;
    }

    @Override
    public Type visitExpression(MiniDecafParser.ExpressionContext ctx) {
        visit(ctx.assignment());
        return null;
    }

    @Override
    public Type visitAssignment(MiniDecafParser.AssignmentContext ctx) {
        if (ctx.children.size() > 1) {
            String name = ctx.IDENT().getText();
            Optional<Symbol> optionSymbol = lookupSymbol(name);
            if (optionSymbol.isPresent()) {
                visit(ctx.expression());
                Symbol symbol = optionSymbol.get();
                stackPop("t0");
                stringBuilder.append("\tsw t0, ").append(symbol.offset).append("(fp)\n");
                stackPush("t0");
                return symbol.type;
            } else {
                reportError("use variable that is not defined", ctx);
            }
        } else {
            visit(ctx.conditional());
        }
        return null;
    }

    @Override
    public Type visitConditional(MiniDecafParser.ConditionalContext ctx) {
        if (ctx.children.size() > 1) {
            int currentCondNo = condNo++;
            visit(ctx.logical_or());
            stackPop("t0");
            stringBuilder.append("\tbeqz t0, .else").append(currentCondNo).append("\n"); // 根据条件表达式判断是否要跳转至 else 分支
            visit(ctx.expression());
            stringBuilder.append("\tj .afterCondition").append(currentCondNo).append("\n"); // 在 then 分支结束后直接跳至分支语句末尾
            stringBuilder.append(".else").append(currentCondNo).append(":\n"); // 在 else 分支结束后直接跳至分支语句末尾
            visit(ctx.conditional());
            stringBuilder.append(".afterCondition").append(currentCondNo).append(":\n");
        } else {
            visit(ctx.logical_or());
        }
        return null;
    }

    @Override
    public Type visitLogical_or(MiniDecafParser.Logical_orContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.logical_or());
            visit(ctx.logical_and());
            stackPop("t1");
            stackPop("t0");
            stringBuilder.append("\tsnez t0, t0\n").append("\tsnez t1, t1\n").append("\tor t0, t0, t1\n");
            stackPush("t0");
        } else {
            visit(ctx.logical_and());
        }
        return null;
    }

    @Override
    public Type visitLogical_and(MiniDecafParser.Logical_andContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.logical_and());
            visit(ctx.equality());
            stackPop("t1");
            stackPop("t0");
            stringBuilder.append("\tsnez t0, t0\n").append("\tsnez t1, t1\n").append("\tand t0, t0, t1\n");
            stackPush("t0");
        } else {
            visit(ctx.equality());
        }
        return null;
    }

    @Override
    public Type visitEquality(MiniDecafParser.EqualityContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.equality());
            visit(ctx.relational());
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "==" -> stringBuilder.append("\tsub t0, t0, t1\n").append("\tseqz t0, t0\n");
                case "!=" -> stringBuilder.append("\tsub t0, t0, t1\n").append("\tsnez t0, t0\n");
            }
            stackPush("t0");
        } else {
            visit(ctx.relational());
        }
        return null;
    }

    @Override
    public Type visitRelational(MiniDecafParser.RelationalContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.relational());
            visit(ctx.additive());
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "<" -> stringBuilder.append("\tslt t0, t0, t1\n");
                case ">" -> stringBuilder.append("\tsgt t0, t0, t1\n").append("\tsnez t0, t0\n");
                case "<=" -> stringBuilder.append("\tsgt t0, t0, t1\n").append("\txori t0, t0, 1\n");
                case ">=" -> stringBuilder.append("\tslt t0, t0, t1\n").append("\txori t0, t0, 1\n");
            }
            stackPush("t0");
        } else {
            visit(ctx.additive());
        }
        return null;
    }

    @Override
    public Type visitAdditive(MiniDecafParser.AdditiveContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.additive());
            visit(ctx.multiplicative());
            // 将加法和减法的操作数存入寄存器
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "+" -> stringBuilder.append("\tadd t0, t0, t1\n");
                case "-" -> stringBuilder.append("\tsub t0, t0, t1\n");
            }
            // 将运算结果存回栈中
            stackPush("t0");
        } else {
            visit(ctx.multiplicative());
        }
        return null;
    }

    @Override
    public Type visitMultiplicative(MiniDecafParser.MultiplicativeContext ctx) {
        // 与加减操作基本相同
        if (ctx.children.size() > 1) {
            visit(ctx.multiplicative());
            visit(ctx.unary());
            stackPop("t1");
            stackPop("t0");
            switch (ctx.children.get(1).getText()) {
                case "*" -> stringBuilder.append("\tmul t0, t0, t1\n");
                case "/" -> stringBuilder.append("\tdiv t0, t0, t1\n");
                case "%" -> stringBuilder.append("\trem t0, t0, t1\n");
            }
            stackPush("t0");
        } else {
            visit(ctx.unary());
        }
        return null;
    }

    @Override
    public Type visitUnary(MiniDecafParser.UnaryContext ctx) {
        if (ctx.children.size() > 1) {
            visit(ctx.unary()); //递归循环
            stackPop("t0");
            switch (ctx.children.get(0).getText()) {
                case "-" -> stringBuilder.append("\tneg t0, t0\n");
                case "~" -> stringBuilder.append("\tnot t0, t0\n");
                case "!" -> stringBuilder.append("\tseqz t0, t0\n");
            }
            stackPush("t0");
        } else {
            visit(ctx.primary());
        }
        return null;
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
        return null;
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
            stringBuilder.append("\tlw t0, ").append(symbol.offset).append("(fp)\n");
            stackPush("t0");
            return symbol.type;
        } else {
            reportError("use variable that is not defined", ctx);
        }
        return null;
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
     * 查询符号表
     *
     * @param v 被查询的变量名
     */
    private Optional<Symbol> lookupSymbol(String v) {
        if (symbolTable.containsKey(v))
            return Optional.of(symbolTable.get(v));
        else
            return Optional.empty();
    }
}