package minidecaf;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

public final class MainVisitor extends MiniDecafBaseVisitor<Type> {
    private final StringBuilder stringBuilder; // 生成的目标汇编代码
    private boolean containsMain = false; // 标志是否有主函数
    private String currentFunction; // 当前函数
    private int localCount; // 局部变量计数
    private Stack<Map<String, Symbol>> symbolTable = new Stack<>(); // 符号表
    private int condCount = 0; // 用于给条件语句和条件表达式所用的标签编号
    private int loopCount = 0; // 用于给循环语句所用的标签编号
    private Stack<Integer> currentLoop = new Stack<>(); // 当前位置的循环标签编号

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
        symbolTable.add(new HashMap<>()); // 为函数开启新的作用域
        visit(ctx.compound_statement());
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
        return null;
    }

    @Override
    public Type visitType(MiniDecafParser.TypeContext ctx) {
        if (!ctx.getText().equals("int")) reportError("class error", ctx);
        return null;
    }

    @Override
    public Type visitCompound_statement(MiniDecafParser.Compound_statementContext ctx) {
        for (var blockItem : ctx.blockitem())
            visit(blockItem);
        return null;
    }

    @Override
    public Type visitDeclaration(MiniDecafParser.DeclarationContext ctx) {
        visit(ctx.type());
        String name = ctx.IDENT().getText();
        if (symbolTable.peek().get(name) != null) // 若重复声明则报错
            reportError("try declaring a declared variable", ctx);
        symbolTable.peek().put(name, new Symbol(name, -4 * ++localCount, new Type.IntType()));// 否则加入符号表
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
            stringBuilder.append("\taddi sp, sp, 4\n");
        }
        return null;
    }

    @Override
    public Type visitIfStatement(MiniDecafParser.IfStatementContext ctx) {
        int currentCondNo = condCount++;
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
    public Type visitDefaultStatement(MiniDecafParser.DefaultStatementContext ctx) {
        symbolTable.add(new HashMap<>()); // 创建新的符号表
        visit(ctx.compound_statement());
        symbolTable.pop(); // 删除该作用域新的符号表
        return null;
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
            visit(ctrl);
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
        return null;
    }

    @Override
    public Type visitWhileStatement(MiniDecafParser.WhileStatementContext ctx) {
        int currentLoop = loopCount++;
        stringBuilder.append(".beforeLoop").append(currentLoop).append(":\n").
                append(".continueLoop").append(currentLoop).append(":\n"); // continue 指令需要跳转到这里
        visit(ctx.expression());
        stackPop("t0");
        stringBuilder.append("\tbeqz t0, .afterLoop").append(currentLoop).append("\n");
        this.currentLoop.push(currentLoop);
        visit(ctx.statement()); // 访问循环体
        this.currentLoop.pop();
        stringBuilder.append("\tj .beforeLoop").append(currentLoop).append("\n").
                append(".afterLoop").append(currentLoop).append(":\n");
        return null;
    }

    @Override
    public Type visitDoWhileStatement(MiniDecafParser.DoWhileStatementContext ctx) {
        int currentLoop = loopCount++;
        stringBuilder.append(".beforeLoop").append(currentLoop).append(":\n");
        this.currentLoop.push(currentLoop);
        visit(ctx.statement()); // 访问循环体
        this.currentLoop.pop();
        stringBuilder.append(".continueLoop").append(currentLoop).append(":\n"); // continue 指令需要跳转到这里
        visit(ctx.expression());
        stackPop("t0");
        stringBuilder.append("\tbnez t0, .beforeLoop").append(currentLoop).append("\n").
                append(".afterLoop").append(currentLoop).append(":\n");
        return null;
    }

    @Override
    public Type visitBreakStatement(MiniDecafParser.BreakStatementContext ctx) {
        if (currentLoop.isEmpty())
            reportError("break statement not within loop", ctx);
        stringBuilder.append("\tj .afterLoop").append(currentLoop.peek()).append("\n");
        return null;
    }

    @Override
    public Type visitContinueStatement(MiniDecafParser.ContinueStatementContext ctx) {
        if (currentLoop.isEmpty())
            reportError("continue statement not within loop", ctx);
        stringBuilder.append("\tj .continueLoop").append(currentLoop.peek()).append("\n");
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
            int currentCondNo = condCount++;
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
}