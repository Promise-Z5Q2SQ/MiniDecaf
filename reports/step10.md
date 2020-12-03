# MiniDecaf实验Step10实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了全局变量的支持，为此新增或修改的语法规范如下。

```
program: (function | global)* EOF;

global: type IDENT ('=' NUM)? ';';
```

要求全局变量的初始值必须是整数字面量，所以必须新增一种语法。在代码中，维护了两个 `HashMap`，分别为已声明和已初始化的全局变量，储存变量名和类型。

```java
private final Map<String, Type> declaredGlobalTable = new HashMap<>();
private final Map<String, Type> initializedGlobalTable = new HashMap<>();
```

其余处理与函数部分类似，也不允许重复声明全局变量，并且与函数定义或声明也不能让重名。

## 思考题

```assembly
lui t1, %hi(a)
lw t0, %lo(a)(t1)
```

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。