# MiniDecaf实验Step6实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了作用域和块语句，为此新增或修改的语法规范如下。

```
function: type IDENT '(' ')' compound_statement;

type: 'int';

compound_statement: '{' blockitem* '}';

blockitem: statement | declaration;

declaration: type IDENT ('=' expression)?;

statement: 'return' expression ';' #returnStatement
    | expression? ';' #expressionStatement
    | 'if' '(' expression ')' statement ('else' statement)? #ifStatement
    | compound_statement #defaultStatement
    ;
```

在代码方面进行的主要改动是将原来储存变量及其位置的符号表 `symbolTable` 从一个单独的 `HashMap` 变为多个，并以栈的形式储存，每次进入新的块语句就为其新建一个作用域的符号表，在退出该块时也要删除该符号表，同时在查询变量时也要从最内层，也就是栈顶开始不断向下查询。

## 思考题

1. 只需在最开始声明 `int x = 0`，在下面的条件语句里走 `else` 分支，虽然其中又有 `int x = 2` 语句，但是由于这里又是声明定义语句而不是赋值语句，程序会在这个块语句内重新构建一个同名变量，而在函数返回时该变量生命周期已经结束，所以返回的依然是 0.

2. 如果被编译的语言的变量声明定义具有提升性质，即在局部作用域中定义，在全局仍可使用，如 python，名称解析作为单独的阶段更好。

    ```python
    if 2 > 1:
    	a = 2
    print(a)
    ```

    上述代码结果是 2.

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。