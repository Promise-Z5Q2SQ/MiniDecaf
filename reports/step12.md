# MiniDecaf实验Step12实验报告

<div style="text-align: center;">2018011358 朱书琦</div>

## 实验内容

本步骤实现了对数组类型的支持，为此新增或修改的语法规范如下。

```
declaration: type IDENT ('=' expression)? ';'   # localIntOrPointerDecl
    | type IDENT ('[' NUM ']')+ ';'	            # localArrayDecl
    ;

global: type IDENT ('=' NUM)? ';'	# globalIntOrPointerDecl
    | type IDENT ('[' NUM ']')+ ';'	# globalArrayDecl
    ;
    
postfix:
	IDENT '(' (expression (',' expression)*)? ')'   # functionPostfix
	| postfix '[' expression ']'                    # arrayPostfix
	| primary                                       # primaryPostfix
	;
```

在代码中，主要为数组类型新增了 `ArrayType` 类，同时实现了相关函数，需要注意的是数组类型变量的大小是由其内容类型所决定。

在新构建一个数组类型变量时，利用双向队列逐层构建多维数组，需要注意的是维度只能是正常数，同时不能对数组变量取引用和解引用。

对于下标运算符，只能作用于指针变量或是数组变量，结果类型是他们的基类型，其余的左值检查和类型检查与上一个 step 类似。

## 思考题

1. ```
    B: *(int*)(4096 + 23 * 4)
    C: *(int*)(4096 + 2 * 10 * 4 + 3 * 4)
    D: *(int*)(*(int*)(4096 + 2 * 4) + 3 * 4)
    E: *(int*)(*(int*)(4096 + 2 * 4 ) + 3 * 4)
    ```

2. 因为如果存在两个以上的变长数组时，至少有一个数组的起始地址不能再编译时确定，所以我们需要根据其长度变量的值在运行时动态分配栈帧，而且编译时也不能确定局部变量的地址，所以在运行时也需要在栈中保存下来。

## 相关参考

如实验内容部分所述，主要参考了实验指导书和所给的 java 样例代码。