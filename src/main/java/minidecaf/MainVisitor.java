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
        stringBuilder.append("\tret\n");
        return null;
    }

    @Override
    public Type visitExpr(MiniDecafParser.ExprContext ctx) {
        TerminalNode num = ctx.NUM();
        BigInteger bigInteger = new BigInteger(num.getText());
        BigInteger maxInteger = new BigInteger(String.valueOf(Integer.MAX_VALUE));
        // 检验数字字面量不能超过整型的最大值
        if (maxInteger.compareTo(bigInteger) <= 0)
            reportError("too large number", ctx);
        stringBuilder.append("\tli a0, ").append(num.getText()).append("\n");
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
}