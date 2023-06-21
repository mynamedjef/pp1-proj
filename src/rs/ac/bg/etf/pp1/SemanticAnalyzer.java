package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	class FunctionData {
		int parCount = 0;
		ArrayList<Struct> arguments = new ArrayList<>();

		public void insert(Struct s) {
			parCount++;
			arguments.add(s);
		}

		public String toString() {
			String s = "";
			for (int i = 0; i < arguments.size(); i++) {
				s += "" + (i+1) + ": " + structToString(arguments.get(i)) + '\n'; 
			}
			return s.equals("") ?
					"no formal parameters\n" :
					s;
		}
	}

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

	public static String structToString(Struct s) {
		String suffix = "";
		while (s.getKind() == Struct.Array) {
			s = s.getElemType();
			suffix += "[]";
		}

		switch(s.getKind()) {
		case 0:	return "NULL"+suffix;
		case 1: return "int"+suffix;
		case 2: return "char"+suffix;
		case 3: return "array"+suffix;
		case 4: return "class"+suffix;
		case 5: return "bool"+suffix;
		default: return "error"+suffix;

		}
	}

	Logger log = Logger.getLogger(getClass());

	public boolean errorDetected = false;

	HashMap<String, FunctionData> allFunctions = new HashMap<>();
	Stack<ArrayList<Struct>> funStack = new Stack<>();

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

	public void printAllFunctionDecls() {
		for (String name : allFunctions.keySet()) {
			String value = allFunctions.get(name).toString();
			System.out.println("----- " + name + " -----\n" + value);
		}
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

		FunctionData fd = allFunctions.get(currentMethod.getName());
		fd.insert(type);
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
				"tipa (" + structToString(currentMethod.getType()) + ')', method);
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
		allFunctions.put(name, new FunctionData());
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
		Obj obj = factor.getDesignator().obj;
		if (obj == Tab.noObj) {
			report_error("Designator [" + obj.getName() + "] ne postoji", factor);
			factor.struct = Tab.noType;
			return;
		}
		factor.struct = obj.getType();
	}

	public void funcCall(Obj obj, SyntaxNode node) {
		report_info("poziv f-je [" + obj.getName() + ']', node);

		if (obj.getKind() != Obj.Meth) {
			report_error("samo se funkcije mogu pozivati", node);
			return;
		}

		ArrayList<Struct> curr = funStack.pop();
		FunctionData fd = allFunctions.get(obj.getName());
		ArrayList<Struct> supposed = fd.arguments;
		if (curr.size() != supposed.size()) {
			report_error("funkcija [" + obj.getName() + "] ocekuje [" + fd.parCount + "] parametara " +
					"a prosledjeno joj je [" + curr.size() + "]", node);
			return;
		}

		for (int i = 0; i < curr.size(); i++) {
			if (!curr.get(i).equals(supposed.get(i))) {
				report_error((i+1)+". parametar funkcije [" + obj.getName() + "] ne odgovara. " +
						"ocekuje se tip (" + structToString(supposed.get(i)) + ") a dobijen je tip (" +
						structToString(curr.get(i)) + ')', node);
			}
		}
	}

	public void visit(FactorFuncCall factor) {
		Obj obj = factor.getFunctionCall().getFunctionName().getDesignator().obj;
		factor.struct = obj.getType();
		if (obj.getType() == Tab.noType) {
			report_error("void funkcija ne moze biti u izrazu", factor);
		}
		funcCall(obj, factor);
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
		Obj obj = desig.getFunctionCall().getFunctionName().getDesignator().obj;
		funcCall(obj, desig);
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

	// ----------------------------------- CondFact -------------------------------------------------

	public void visit(CondFactRelop cond) {
		Struct s1 = cond.getExpr().struct;
		Struct s2 = cond.getExpr1().struct;
		if (!s1.compatibleWith(s2)) {
			report_error("nekompatibilni tipovi u poredjenju", cond);
			return;
		}
		Relop r = cond.getRelop();
		if (s1.getKind() == Struct.Array && !(r instanceof RelopEQ) && !(r instanceof RelopNEQ)) {
			report_error("nizovi mogu da se porede samo sa EQ i NEQ", cond);
		}
	}

	public void visit(CondFactExpr cond) {
		if (cond.getExpr().struct != booleanType) {
			report_error("uslov mora biti tipa boolean", cond);
		}
	}

	public void visit(ActPar act) {
		report_info("par " + structToString(act.getExpr().struct), act);
		ArrayList<Struct> l = funStack.peek();
		l.add(act.getExpr().struct);
	}

	// ----------------------------------- FunctionCall ---------------------------------------------

	public void visit(FunctionName fun) {
		ArrayList<Struct> l = new ArrayList<>();
		funStack.push(l);
		report_info("fun " + fun.getDesignator().obj.getName(), fun);
	}

	// ----------------------------------- Statements -----------------------------------------------

	public void visit(MatchedMap stmt) {
		Obj obj = stmt.getDesignator().obj;
		Struct type = obj.getType();
		if (type.getKind() != Struct.Array ||
			(type.getKind() == Struct.Array && type.getElemType().getKind() == Struct.Array))
		{
			report_error("promenljiva [" + obj.getName() +
					"] mora biti jednodimenzionalni niz ugradjenog tipa u map iskazu", stmt);
		}
		Obj iter = Tab.find(stmt.getIter());
		if (iter == Tab.noObj) {
			report_error("identifikator [" + stmt.getIter() + "] ne postoji", stmt);
			return;
		}
		if (!iter.getType().equals(obj.getType().getElemType())) {
			report_error("tip identifikatora i tip niza [" + obj.getName() + "] se moraju poklapati", stmt);
		}
	}

	public void visit(MatchedPrintExpr stmt) {
		Struct type = stmt.getExpr().struct;
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			report_error("print izraz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}

	public void visit(MatchedPrintNum stmt) {
		Struct type = stmt.getExpr().struct;
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			report_error("print iskaz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}

	public void visit(MatchedRead stmt) {
		Obj obj = stmt.getDesignator().obj;
		Struct type = obj.getType();
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			report_error("read iskaz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}
}
