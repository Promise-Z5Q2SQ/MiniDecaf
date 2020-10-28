# MiniDecaf实验Step5实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了局部变量的声明定义与使用，为此新增或修改的语法规范如下。

```
function: type IDENT '(' ')' '{' statement* '}';

type: 'int';

statement: 'return' expression ';' #returnStatement
    | expression? ';' #expressionStatement
    | type IDENT ('=' expression)? ';' #declarationStatement
    ;

expression : assignment;

assignment : logical_or | IDENT '=' expression;

primary: NUM #numberPrimary
    | '(' expression ')' #parenthesizedPrimary
    | IDENT #identPrimary
    ;
```

在处理非终结符 statement 和 primary 的过程中，模仿参考实现使用了为每个子规则进行了命名，更方便区分不同的情况。

对于栈的空间处理与实验指导中略有不同，我实现时采用了与参考实现相同的策略，即认为栈帧指示器 fp 指向局部变量开始的地址，所以整个栈帧的大小（无视运算栈）为 `4 * 局部变量个数`。另外有一个 `HashMap` 类数据结构储存对应变量名和其相关信息结构的映射关系，需要时新建或查询调用，当前的符号表只有主函数中的一个。

## 思考题

1. 如上描述，运行中函数栈帧分为三部分，最高位是函数的返回地址和上一个栈帧起始地址，共 8 字节；随后是局部变量，大小为 `4 * 局部变量个数`，再随后是运算栈，大小随计算过程而动态变化，但每条语句开始前和结束后大小都为 0。
2. 处理声明语句时，无需检查是否已被定义，同时需要先计算等号后表达式的值，再进行局部变量栈的储存，加入符号表。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。