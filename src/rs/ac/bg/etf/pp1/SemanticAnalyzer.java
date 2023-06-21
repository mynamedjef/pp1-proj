package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

	Logger log = Logger.getLogger(getClass());

	public boolean errorDetected = false;

	private Obj currentMethod = null;
	private Struct declarationType = null;
	private Struct returnType = null;
	private int nVars = -1;

	public void report_error(String message, SyntaxNode node) {
		errorDetected = true;
		String cls = "\t<" + Colors.ANSI_RESET + node.getClass().getSimpleName() + Colors.ANSI_RED + ">\t\t";
		String err = Colors.ANSI_RED + "Semantika (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_RED + "): " + cls;
		log.error(err + message + Colors.ANSI_RESET);
	}

	public void report_info(String message, SyntaxNode node) {
		String cls = "\t<" + Colors.ANSI_RESET + node.getClass().getSimpleName() + Colors.ANSI_GREEN + ">\t\t";
		String err = Colors.ANSI_GREEN + "Info      (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_GREEN + "): " + cls;
		log.info(err + message + Colors.ANSI_RESET);
	}

	private Struct declare_array(Struct type) {
		return new Struct(Struct.Array, type);
	}

	private Struct declare_matrix(Struct type) {
		return new Struct(Struct.Array, declare_array(type));
	}
	// ======================================== VISITI =============================================

	public void visit(ProgName progName) {
		progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
		Tab.openScope();
	}

	public void visit(Program prog) {
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(prog.getProgName().obj);
		Tab.closeScope();
	}

	// -------------------------------------- ConstDecl ---------------------------------------------

	public void visit(ConstDecl cnst) {
		declarationType = null;
	}

	public void visit(ConstOneNumber cnst) {
		cnst.struct = Tab.intType;
	}

	public void visit(ConstOneChar cnst) {
		cnst.struct = Tab.charType;
	}

	public void visit(ConstOneBool cnst) {
		cnst.struct = booleanType;
	}

	public void visit(ConstAssign constAssign) {
		String name = constAssign.getName();
		if (Tab.find(name) != Tab.noObj) {
			report_error("konstanta " + name + " je vec deklarisana", constAssign);
			return;
		}

		if (!constAssign.getConstOne().struct.assignableTo(declarationType)) {
			report_error("tip konstante [" + name + "] i deklaracija se ne poklapaju", constAssign);
			return;
		}

		Tab.insert(Obj.Con, constAssign.getName(), declarationType);
		report_info("konstanta [" + name + "] definisana", constAssign);
	}

	public void visit(Type type) {
		Obj typeNode = Tab.find(type.getTypeName());
		type.struct = Tab.noType;
		declarationType = Tab.noType;

		if (typeNode == Tab.noObj) {
			report_error("Ne postoji tip [" + type.getTypeName() + ']', type);
			return;
		}

		if (typeNode.getKind() != Obj.Type) {
			report_error("Ne postoji tip 2 [" + type.getTypeName() + ']', type);
			return;
		}

		declarationType = type.struct = typeNode.getType();
	}

	// -------------------------------------- VarDecl -----------------------------------------------

	public void processVarDecl(VarDeclSingle var, String name, Struct type, String msg) {
		if (Tab.currentScope.findSymbol(name) != null) {
			report_error("simbol [" + name + "] vec postoji u trenutnom scope-u", var);
			return;
		}

		Tab.insert(Obj.Var, name, type);
		report_info(msg + " [" + name + "] deklarisana", var);
	}

	public void visit(VarDeclScalar var) {
		processVarDecl(var, var.getName(), declarationType, "promenljiva");
	}

	public void visit(VarDeclArray var) {
		processVarDecl(var, var.getName(), declare_array(declarationType), "niz");
	}

	public void visit(VarDeclMatrix var) {
		processVarDecl(var, var.getName(), declare_matrix(declarationType), "matrica");
	}

	// -------------------------------------- FormPars ----------------------------------------------

	public void processFormPars(FormPar var, String name, Struct type, String msg) {
		if (Tab.currentScope.findSymbol(name) != null) {
			report_error("simbol [" + name + "] vec postoji u trenutnom scope-u", var);
			return;
		}

		Tab.insert(Obj.Var, name, type);
		report_info("formpar " + msg + " [" + name + "] deklarisan", var);
	}

	public void visit(FormParScalar form) {
		processFormPars(form, form.getName(), form.getType().struct, "skalar");
	}

	public void visit(FormParArray form) {
		processFormPars(form, form.getName(), declare_array(form.getType().struct), "niz");
	}

	public void visit(FormParMatrix form) {
		processFormPars(form, form.getName(), declare_matrix(form.getType().struct), "matrica");
	}

	// -------------------------------------- MethodDecl --------------------------------------------

	public void visit(MethodDecl method) {
		boolean b2 = returnType != null && currentMethod.getType() != returnType;
		if (b2 || (returnType == null && currentMethod.getType() != Tab.noType)) {
			report_error("metoda [" + currentMethod.getName() + "] mora imati return naredbu " +
				"tipa " + currentMethod.getType().getKind(), method);
		}

		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		currentMethod = null;
		returnType = null;
	}

	public void processMethod(MethodTypeAndName method, String name, Struct type) {
		if (Tab.currentScope.findSymbol(name) != null) {
			report_error("Metoda [" + name + "] je vec definisana", method);
			return;
		}

		currentMethod = Tab.insert(Obj.Meth, name, type);
		Tab.openScope();
	}

	public void visit(MethodTypeAndNameNonvoid method) {
		processMethod(method, method.getName(), method.getType().struct);
	}

	public void visit(MethodTypeAndNameVoid method) {
		processMethod(method, method.getName(), Tab.noType);
	}

	// ---------------

	public void visit(MatchedReturnExpr matched) {
		if (currentMethod == null) {
			report_error("return ne moze biti van funkcije", matched);
			return;
		}

		returnType = matched.getExpr().struct;
	}

	// -------------------------------------- Expr --------------------------------------------------

	public void visit(ExprAddop expr) {
		if (expr.getExpr().struct != Tab.intType || expr.getTerm().struct != Tab.intType) {
			report_error("tokom Addop expr i term moraju biti oba tipa int", expr);
			expr.struct = Tab.noType;
			return;
		}
		expr.struct = Tab.intType;
	}

	public void visit(ExprNegative expr) {
		if (expr.getTerm().struct != Tab.intType) {
			report_error("izraz unarnog minusa mora biti int tipa", expr);
			expr.struct = Tab.noType;
			return;
		}
		expr.struct = Tab.intType;
	}

	public void visit(ExprTerm expr) {
		expr.struct = expr.getTerm().struct;
	}

	// -------------------------------------- Term --------------------------------------------------

	public void visit(TermMulop term) {
		if (term.getTerm().struct != Tab.intType || term.getFactor().struct != Tab.intType) {
			report_error("tokom Mulop term i factor moraju biti oba tipa int", term);
			term.struct = Tab.noType;
			return;
		}
		term.struct = Tab.intType;
	}

	public void visit(TermFactor term) {
		term.struct = term.getFactor().struct;
	}

	// -------------------------------------- Factor ------------------------------------------------

	public void visit(FactorDesignator factor) {
		Obj obj = Tab.find(factor.getDesignator().obj.getName());
		if (obj == Tab.noObj) {
			report_error("Designator [" + obj.getName() + "] ne postoji", factor);
			factor.struct = Tab.noType;
			return;
		}
		factor.struct = obj.getType();
	}

	public void visit(FactorFuncCall factor) {
		Obj obj = factor.getDesignator().obj;
		if (obj.getType() == Tab.noType) {
			report_error("void funkcija ne moze biti u izrazu", factor);
		}
		factor.struct = obj.getType();
	}

	public void visit(FactorConst factor) {
		factor.struct = factor.getConstOne().struct;
	}

	public void visit(FactorNewArray factor) {
		if (factor.getExpr().struct != Tab.intType) {
			report_error("Izraz indeksiranja mora biti tipa int", factor);
			factor.struct = Tab.noType;
			return;
		}
		factor.struct = declare_array(factor.getType().struct);
	}

	public void visit(FactorNewMatrix factor) {
		if (factor.getExpr().struct != Tab.intType || factor.getExpr1().struct != Tab.intType) {
			report_error("Izraz indeksiranja mora biti tipa int", factor);
			factor.struct = Tab.noType;
			return;
		}
		factor.struct = declare_matrix(factor.getType().struct);
	}

	public void visit(FactorExpr factor) {
		factor.struct = factor.getExpr().struct;
	}

	// -------------------------------------- Designator --------------------------------------------

	public void visit(DesignatorName desig) {
		desig.obj = Tab.find(desig.getName());
		if (desig.obj == Tab.noObj) {
			report_error("Ime designatora [" + desig.getName() + "] ne postoji", desig);
		}
	}

	public void visit(DesignatorArray desig) {
		Obj obj = desig.getDesignator().obj;
		desig.obj = Tab.noObj;
		if (desig.getExpr().struct != Tab.intType) {
			report_error("Tip izraza kojim se indeksira promenljiva [" + desig.obj.getName() + "] mora biti int", desig);
			return;
		}
		if (obj.getType().getKind() != Struct.Array) {
			report_error("Promenljiva [" + obj.getName() + "] mora biti tipa niz", desig);
			return;
		}

		desig.obj = new Obj(Obj.Elem, obj.getName(), obj.getType().getElemType());
	}

	// -------------------------------- DesignatorStatement -----------------------------------------

	// TODO ZA MATRICU
	public void visit(DesignatorStatementAssign desig) {
		Obj d = desig.getDesignator().obj;
		Struct e = desig.getExpr().struct;

		if (!e.assignableTo(d.getType())) {
			report_error("tip promenljive [" + d.getName() + "] se ne poklapa sa tipom dodeljene vrednosti", desig);
		}
		if (d.getKind() != Obj.Var && d.getKind() != Obj.Elem) {
			report_error("designator [" + d.getName() + "] mora biti promenljiva ili element niza/matrice", desig);
		}
	}

	public void visit(DesignatorStatementFuncCall desig) {
		Obj obj = desig.getDesignator().obj;

		if (obj.getKind() != Obj.Meth) {
			report_error("moze se pozvati samo funkcija", desig);
		}
	}

	public void visit(DesignatorStatementIncr desig) {
		Obj obj = desig.getDesignator().obj;

		if (obj.getType() != Tab.intType || (obj.getKind() != Obj.Var && obj.getKind() != Obj.Elem)) {
			report_error("moze se inkrementirati samo integer", desig);
		}
	}

	public void visit(DesignatorStatementDecr desig) {
		Obj obj = desig.getDesignator().obj;

		if (obj.getType() != Tab.intType || (obj.getKind() != Obj.Var && obj.getKind() != Obj.Elem)) {
			report_error("moze se dekrementirati samo integer", desig);
		}
	}
}
