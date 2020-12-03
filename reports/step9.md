# MiniDecaf实验Step9实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了多函数的功能，包括声明、定义和调用，为此新增或修改的语法规范如下。

```
program: function* EOF;

function: type IDENT '(' (type IDENT (',' type IDENT)*)? ')' compound_statement #defineFunction
    | type IDENT '(' (type IDENT (',' type IDENT)*)? ')' ';' #declareFunction
    ;

unary: postfix | ('-'|'!'|'~') unary;

postfix: primary | IDENT '(' (expression (',' expression)*)? ')';
```

在代码中，维护了两个 `HashMap`，分别为已声明的和已定义的函数表，保存了他们的函数名和参数、返回值类型。

```java
private final Map<String, FunctionType> declaredFunctionTable = new HashMap<>(); // 已声明函数表
private final Map<String, FunctionType> definedFunctionTable = new HashMap<>(); // 已定义函数表
```

并且直接让所有函数都默认返回0，以满足 main 之外的函数没有 return 是未定义行为。同时，每个函数只能被定义一次，但在定义之前可能有一次前置声明，并且为了实现方便，多次声明一个函数、以及定义后再声明，均是被允许的，但多次定义一个函数是错误，以及函数声明和定义的参数个数、同一位置的参数类型、以及返回值类型必须相同。

对于函数的定义和调用中参数的位置，遵循 riscv gcc 的调用约定，前8个参数使用寄存器 a0-a7 储存，剩余参数位于内存中，位于 ra 前，参数声明所在作用域就是函数体的块语句对应作用域，参数声明可以看成在函数体开头的声明，因此函数体块语句中不能重新声明参数。

## 思考题

```c
int add(int a, int b) {
    return a + 2 * b;
}

int main() {
    int x = 1;
    int sum = add(x, x++);
    return sum;
}
```

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。