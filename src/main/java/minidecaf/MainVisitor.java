package minidecaf;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;

public final class MainVisitor extends MiniDecafBaseVisitor<Type> {
    private final StringBuilder stringBuilder; // 生成的目标汇编代码
    private boolean containsMain = false; //标志是否有主函数

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
        String ident = ctx.IDENT().getText();
        if (ident.equals("main")) containsMain = true; // 出现主函数即记录
        stringBuilder.append("\t.text\n");// 表示以下内容在 text 段中
        stringBuilder.append("\t.global ").append(ident).append("\n"); // 让该 label 对链接器可见
        stringBuilder.append(ident).append(":\n");
        visit(ctx.stmt());
        return null;
    }

    @Override
    public Type visitType(MiniDecafParser.TypeContext ctx) {
        if (!ctx.getText().equals("int")) reportError("return class error", ctx);
        return null;
    }

    @Override
    public Type visitStmt(MiniDecafParser.StmtContext ctx) {
        visit(ctx.expr());
        // 函数返回，返回值存在 a0 中
        stackPop("a0");
        stringBuilder.append("\tret\n");
        return null;
    }

    @Override
    public Type visitExpr(MiniDecafParser.ExprContext ctx) {
        visit(ctx.unary());
        return null;
    }

    @Override
    public Type visitUnary(MiniDecafParser.UnaryContext ctx) {
        if (ctx.children.size() == 1) {
            TerminalNode num = ctx.NUM();
            BigInteger bigInteger = new BigInteger(num.getText());
            BigInteger maxInteger = new BigInteger(String.valueOf(Integer.MAX_VALUE));
            // 检验数字字面量不能超过整型的最大值
            if (maxInteger.compareTo(bigInteger) <= 0)
                reportError("too large number", ctx);
            stringBuilder.append("\tli t0, ").append(num.getText()).append("\n");
        } else { //执行一元运算
            visit(ctx.unary()); //递归循环
            stackPop("t0");
            switch (ctx.children.get(0).getText()) {
                case "-" -> stringBuilder.append("\tneg t0, t0\n");
                case "~" -> stringBuilder.append("\tnot t0, t0\n");
                case "!" -> stringBuilder.append("\tseqz t0, t0\n");
            }
        }
        stackPush("t0");
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
}