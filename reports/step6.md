# MiniDecaf实验Step6实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了条件语句和条件表达式，为此新增或修改的语法规范如下。

```
function: type IDENT '(' ')' '{' blockitem* '}';

type: 'int';

blockitem: statement | declaration;

declaration: type IDENT ('=' expression)?;

statement: 'return' expression ';' #returnStatement
    | expression? ';' #expressionStatement
    | 'if' '(' expression ')' statement ('else' statement)? # ifStatement
    ;

expression: assignment;

assignment : conditional | IDENT '=' expression;

conditional: logical_or | logical_or '?' expression ':' conditional;
```

需要注意的是多个 if，else嵌套时，悬挂 else 需要依照就近原则，优先和最近的还没有匹配的 if 匹配，上面的语法规范可以直接体现这一点；此外，if 或 else 的语句不能只是一句声明，所以在上面的语法规范中将 statement 和 declaration 分开，也就能实现。

汇编代码生成过程中，需要先根据条件表达式判断是否要跳转至 else 分支，并在 then 分支和 else 分支结束后直接跳至分支语句末尾。

## 思考题

强制要求大括号包裹可以消除悬挂 else 的歧义，让 if else 正确按程序员想法配对。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。