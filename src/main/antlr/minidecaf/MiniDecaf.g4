grammar MiniDecaf;

program: (function | global)* EOF;

function: type IDENT '(' (type IDENT (',' type IDENT)*)? ')' compound_statement #defineFunction
    | type IDENT '(' (type IDENT (',' type IDENT)*)? ')' ';'                    #declareFunction
    ;

type: 'int' '*'*;

compound_statement: '{' blockitem* '}';

blockitem: statement | declaration;

declaration: type IDENT ('=' expression)? ';'   # localIntOrPointerDecl
    | type IDENT ('[' NUM ']')+ ';'	            # localArrayDecl
    ;

global: type IDENT ('=' NUM)? ';'	# globalIntOrPointerDecl
    | type IDENT ('[' NUM ']')+ ';'	# globalArrayDecl
    ;

statement: 'return' expression ';'                                                          #returnStatement
    | expression? ';'                                                                       #expressionStatement
    | 'if' '(' expression ')' statement ('else' statement)?                                 #ifStatement
    | compound_statement                                                                    #defaultStatement
    | 'for' '(' (declaration | expression? ';') expression? ';' expression? ')' statement   #forStatement
    | 'while' '(' expression ')' statement                                                  #whileStatement
    | 'do' statement 'while' '(' expression ')' ';'                                         #doWhileStatement
    | 'break' ';'                                                                           #breakStatement
    | 'continue' ';'                                                                        #continueStatement
    ;

expression: assignment;

assignment : conditional | unary '=' expression;

conditional: logical_or | logical_or '?' expression ':' conditional;

logical_or: logical_and | logical_or '||' logical_and;

logical_and: equality | logical_and '&&' equality;

equality: relational | equality ('=='|'!=') relational;

relational: additive | relational ('<'|'>'|'<='|'>=') additive;

additive: multiplicative | additive ('+'|'-') multiplicative;

multiplicative: unary | multiplicative ('*'|'/'|'%') unary;

unary:
	('-' | '~' | '!' | '&' | '*') unary # operatorUnary
	| '(' type ')' unary                # castUnary
	| postfix                           # postfixUnary;

postfix:
	IDENT '(' (expression (',' expression)*)? ')'   # functionPostfix
	| postfix '[' expression ']'                    # arrayPostfix
	| primary                                       # primaryPostfix
	;

primary: NUM                #numberPrimary
    | '(' expression ')'    #parenthesizedPrimary
    | IDENT                 #identPrimary
    ;

/* lexer */
WS: [ \t\r\n\u000C] -> skip; //空白

// comment
// The specification of minidecaf doesn't allow commenting,
// but we provide the comment feature here for the convenience of debugging.
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;

IDENT: [a-zA-Z_] [a-zA-Z_0-9]*; //合法函数名
NUM: [0-9]+;
