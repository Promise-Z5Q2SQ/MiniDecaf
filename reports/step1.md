# MiniDecaf实验Step1实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤中我们要完成编译的程序仅含有一个`main`函数，并且只需要实现`return`语句，返回一个非负整数。

按照实验指导书的思路，对输入的源代码先进行**词法分析**，获得词（token）组成的流（token stream），再通过**语法分析**对其处理获得一棵语法树（syntax tree），最后通过**Visitor 模式**完成对其的遍历，让对应的语法片段变为对应的目标 riscv 代码。

首先如下在`MiniDecaf.g4`文件中规定用到的语法规范，其中`IDENT`表示合法的函数名，`NUM`表示非负整数。

```
program: function EOF;

function: type IDENT '(' ')' '{' stmt '}';

type: 'int';

stmt: 'return' expr ';';

expr: NUM;
```

随后我们使用的`antlr`插件便会自动帮我们生成`lexer`，`parser`和`visitor`类，我们只需要继承`MiniDecafBaseVisitor<Type>`类并实现其中对每一个非终结符的`visit`函数即可，其中`Type`是自定义的分析树节点类型，按照样例代码中注释所说，遍历时会为每一个分析树节点计算一个类型，对于没有类型的节点用`NoType`留空，这样做的目的在前 10 个 step 并不会体现出来，是为了方便 step 11 和 12 所需的对于表达式的类型推导，于是我将其完全保留，但实际上在 Step1 中完全不用到`Type`类也是可行的。

Visitor 模式的整体思路参考借鉴了所给的 java 样例，需要注意的是在对 expr 的访问中，需要判断数字是否超过`int`的范围，程序中采用了转化为`BigInteger`类的方式加以判断。

## 思考题

1. 修改`minilexer`的输入（`lexer.setInput` 的参数），使得 lexer 报错，给出一个简短的例子。

    为了让 lexer 出错，只需出现不存在的 token，比如任意位置加入一个汉字即可。

    ```c
    int 主函数() {
        return 0;
    }
    ```

2. 修改`minilexer`的输入，使得 lexer 不报错但 parser 报错，给出一个简短的例子。

    为了让 parser 出错，只需出现未定义的语法，

    ```c
    int main() {
        return 0;
    }}
    ```

3. 在 riscv 中，哪个寄存器是用来存储函数返回值的？

    RISC-V 约定`a0`保存返回值，之后`ret`就完成了`return X`的工作。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。