package minidecaf;

public class Compiler {
    public String run() {
        return "" +
"            .text\n" +
"            .globl  main\n" +
"    main:\n" +
"            li      a0,123\n" +
"            ret\n";
    }
}
