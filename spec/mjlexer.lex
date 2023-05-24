
package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\r" 	{ }
"\n" 	{ }
"\f" 	{ }

"%"        { return new_symbol(sym.PERCENT, yytext()); }
"&&"       { return new_symbol(sym.AND, yytext()); }
"("        { return new_symbol(sym.LPAREN, yytext()); }
")"        { return new_symbol(sym.RPAREN, yytext()); }
"*"        { return new_symbol(sym.STAR, yytext()); }
"++"       { return new_symbol(sym.INCR, yytext()); }
"+"        { return new_symbol(sym.PLUS, yytext()); }
","        { return new_symbol(sym.COMMA, yytext()); }
"--"       { return new_symbol(sym.DECR, yytext()); }
"-"        { return new_symbol(sym.MINUS, yytext()); }
"."        { return new_symbol(sym.DOT, yytext()); }
"/"        { return new_symbol(sym.SLASH, yytext()); }
";"        { return new_symbol(sym.SEMI, yytext()); }
"="        { return new_symbol(sym.EQUAL, yytext()); }
"=>"       { return new_symbol(sym.LAMBDA, yytext()); }
">="       { return new_symbol(sym.BEQ, yytext()); }
"<"        { return new_symbol(sym.LANGLE, yytext()); }
"<="       { return new_symbol(sym.LEQ, yytext()); }
"=="       { return new_symbol(sym.EQ, yytext()); }
"!="       { return new_symbol(sym.NEQ, yytext()); }
">"        { return new_symbol(sym.RANGLE, yytext()); }
"["        { return new_symbol(sym.LBRACKET, yytext()); }
"]"        { return new_symbol(sym.RBRACKET, yytext()); }
"break"    { return new_symbol(sym.BREAK, yytext()); }
"continue" { return new_symbol(sym.CONT, yytext()); }
"else"     { return new_symbol(sym.ELSE, yytext()); }
"map"      { return new_symbol(sym.MAP, yytext()); }
"if"       { return new_symbol(sym.IF, yytext()); }
"new"      { return new_symbol(sym.NEW, yytext()); }
"print"    { return new_symbol(sym.PRINT, yytext()); }
"read"     { return new_symbol(sym.READ, yytext()); }
"return"   { return new_symbol(sym.RETURN, yytext()); }
"while"    { return new_symbol(sym.WHILE, yytext()); }
"void"     { return new_symbol(sym.VOID, yytext()); }
"const"    { return new_symbol(sym.CONST, yytext()); }
"class"    { return new_symbol(sym.CLASS, yytext()); }
"extends"  { return new_symbol(sym.EXTENDS, yytext()); }
"{"        { return new_symbol(sym.LBRACE, yytext()); }
"||"       { return new_symbol(sym.OR, yytext()); }
"}"        { return new_symbol(sym.RBRACE, yytext()); }
"program"  { return new_symbol(sym.PROGRAM, yytext()); }

"//" {yybegin(COMMENT);}
<COMMENT> . {yybegin(COMMENT);}
<COMMENT> "\n" { yybegin(YYINITIAL); }

[0-9]+  { return new_symbol(sym.NUMBER, Integer.valueOf(yytext())); }
([a-z]|[A-Z])[a-z|A-Z|0-9|_]* 	{return new_symbol(sym.IDENT, yytext()); }

. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }

