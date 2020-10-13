# MiniDecaf实验Step3实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了算数二元运算符的编译，为此新增的语法规范如下。

```
expression: logical_or;

logical_or: logical_and | logical_or '||' logical_and;

logical_and: equality | logical_and '&&' equality;

equality: relational | equality ('=='|'!=') relational;

relational: additive | relational ('<'|'>'|'<='|'>=') additive;
```

与step3中的过程基本完全相同，在代码中对以上新出现的每个非终结符增加了 `visit` 函数，按照其各自子节点的数目来分类讨论。

在涉及到运算优先级的问题上，也是按照优先级从高到低在语法规范中自底向上构建语法分析树。在同级运算中也是遵循从左到右的顺序，所以将优先结合左边的表达式。

## 思考题

1. ​	在我们自己实现的编译器中一定要先计算出所有的操作数的结果才能进行运算，但实际的c语言中，由于逻辑运算存在惰性机制，所以不需要先计算出所有的操作数的结果。
2. 短路求值（惰性机制）可以使后续的逻辑条件是建立在满足前一个的基础上，如 `t!=0||100/t>10`，如果不存在这样的机制就需要用一个 if else 语句，使代码复杂。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。