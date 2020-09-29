grammar MiniDecaf;

program: function EOF;

function: type IDENT '(' ')' '{' stmt '}';

type: 'int';

stmt: 'return' expr ';';

expr: NUM;

/* lexer */
WS: [ \t\r\n\u000C] -> skip; //空白

// comment
// The specification of minidecaf doesn't allow commenting,
// but we provide the comment feature here for the convenience of debugging.
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;

IDENT: [a-zA-Z_] [a-zA-Z_0-9]*; //合法函数名
NUM: [0-9]+;
