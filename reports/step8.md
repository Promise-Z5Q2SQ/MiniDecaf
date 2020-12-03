# MiniDecaf实验Step8验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了几种不同的循环语句，为此新增或修改的语法规范如下。

```
statement: 'return' expression ';' #returnStatement
    | expression? ';' #expressionStatement
    | 'if' '(' expression ')' statement ('else' statement)? #ifStatement
    | compound_statement #defaultStatement
    | 'for' '(' (declaration | expression?) ';' expression? ';' expression? ')' statement #forStatement
    | 'while' '(' expression ')' statement #whileStatement
    | 'do' statement 'while' '(' expression ')' ';' #doWhileStatement
    | 'break' ';' #breakStatement
    | 'continue' ';' #continueStatement
    ;
```

在代码中，与条件语句类似，新增了一个计数变量 `loopCount`，同时，为了清楚 `break` 和 `continue` 语句前往的语句，用一个栈的数据结构维护当前循环的调用关系。在对每一种循环语句进行处理时，也都要标记 `break` 和 `continue` 语句将跳转的位置，同时循环体中也要创建新作用域的符号表。

## 思考题

假设循环体执行了一次，即第二次判断条件语句时退出循环，第一种执行两次跳转语句（6，3），第二种执行一次跳转语句（7），所以第二种翻译方式更好。

但同时第二种会使得生成的代码更长（cond 的 IR出现两遍）。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。