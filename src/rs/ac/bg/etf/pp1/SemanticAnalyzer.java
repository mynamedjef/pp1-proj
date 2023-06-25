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

		public FunctionData() {}

		public FunctionData(Struct s) {
			insert(s);
		}

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

	class MapData {
		private int adr;
		Expr e;

		public MapData(Expr e) {
			this.e = e;
		}

		void setAdr(int adr) {
			this.adr = adr;
		}

		int getAdr() { return adr; }
	}

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

	public static Obj mapIterator = null;

	public static String structToString(Struct s) {
		String suffix = "";
		while (s.getKind() == Struct.Array) {
			s = s.getElemType();
			suffix += "[]";
		}

		switch(s.getKind()) {
		case 0:	return "void"+suffix;
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
	HashMap<MatchedMap, MapData> mapStatements = new HashMap<>();

	private Obj currentMethod = null;
	private Struct declarationType = null;
	private Struct returnType = Tab.noType;
	private int loopCount = 0;
	public int nVars = -1;

	public String create_message(String message, SyntaxNode node, String color, String msgType) {
		int size = 30 - node.getClass().getSimpleName().length(); // ni jedna klasa nece valjda imati duze ime od 30
		String pad = new String(new char[size]).replace('\0', ' ');

		int size2 = 12 - msgType.length() - String.format("%d", node.getLine()).length();
		String pad2 = new String(new char[size2]).replace('\0', ' ');

		String cls = String.format("%s<%s%s%s>", pad2, Colors.ANSI_RESET, node.getClass().getSimpleName(), color);
		String line = String.format("%s%s (%s%d%s): ", color, msgType, Colors.ANSI_RESET, node.getLine(), color);
		return line + cls + pad + message + Colors.ANSI_RESET;
	}

	public void report_error(String message, SyntaxNode node) {
		errorDetected = true;
		log.error(create_message(message, node, Colors.ANSI_RED, "Semantika"));
	}

	public void report_info(String message, SyntaxNode node) {
		log.info(create_message(message, node, Colors.ANSI_GREEN, "Info"));
	}

	public void printAllFunctionDecls() {
		for (String name : allFunctions.keySet()) {
			String value = allFunctions.get(name).toString();
			System.out.println(String.format("----- %s -----\n%s", name, value));
		}
	}

	private Struct declare_array(Struct type) {
		return new Struct(Struct.Array, type);
	}

	private Struct declare_matrix(Struct type) {
		return new Struct(Struct.Array, declare_array(type));
	}

	// ======================================== VISITI =============================================

	public void addBuiltinMethods() {
		FunctionData chr = new FunctionData(Tab.intType);
		FunctionData ord = new FunctionData(Tab.charType);
		FunctionData len = new FunctionData(Tab.noType);
		allFunctions.put("chr", chr);
		allFunctions.put("ord", ord);
		allFunctions.put("len", len);
	}

	public void visit(ProgName progName) {
		addBuiltinMethods();

		progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
		Tab.openScope();

		mapIterator = Tab.insert(Obj.Var, "map_iterator", Tab.intType);
		mapIterator.setLevel(0);
	}

	public void visit(Program prog) {
		if (!allFunctions.containsKey("main")) {
			report_error("program mora imati funkciju [main]", prog);
		}
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(prog.getProgName().obj);
		Tab.closeScope();
	}

	// -------------------------------------- ConstDecl ---------------------------------------------

	public void visit(ConstDecl cnst) {
		declarationType = null;
	}

	public void visit(ConstOneNumber cnst) {
		cnst.obj = new Obj(Obj.Con, "", Tab.intType, cnst.getN1(), 0);
	}

	public void visit(ConstOneChar cnst) {
		cnst.obj = new Obj(Obj.Con, "", Tab.charType, cnst.getC1(), 0);
	}

	public void visit(ConstOneBool cnst) {
		cnst.obj = new Obj(Obj.Con, "", booleanType, cnst.getB1()?1:0, 0);
	}

	public void visit(ConstAssign constAssign) {
		String name = constAssign.getName();
		if (Tab.find(name) != Tab.noObj) {
			report_error("konstanta " + name + " je vec deklarisana", constAssign);
			return;
		}

		if (!constAssign.getConstOne().obj.getType().assignableTo(declarationType)) {
			report_error("tip konstante [" + name + "] i deklaracija se ne poklapaju", constAssign);
			return;
		}

		Obj obj = Tab.insert(Obj.Con, constAssign.getName(), declarationType);
		Obj kid = constAssign.getConstOne().obj;
		obj.setAdr(kid.getAdr());
		obj.setLevel(0);
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
		String name = currentMethod.getName();
		Struct type = currentMethod.getType();

		if (name.equals("main")) {
			ArrayList<Struct> arguments = allFunctions.get(name).arguments;
			if (type != Tab.noType) {
				report_error(String.format("metoda [main] mora biti tipa (void)"), method);
			}
			if (arguments.size() > 0) {
				report_error("metoda [main] ne sme primati argumente", method);
			}
		}
		if (returnType != type) {
			report_error(String.format("tip povratne vrednosti (%s) se ne poklapa sa tipom (%s) funkcije [%s]",
					structToString(returnType), structToString(type), name), method);
		}

		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		currentMethod = null;
		returnType = Tab.noType;
	}

	public void processMethod(MethodTypeAndName method, String name, Struct type) {
		if (Tab.currentScope.findSymbol(name) != null) {
			report_error("Metoda [" + name + "] je vec definisana", method);
			return;
		}

		method.obj = currentMethod = Tab.insert(Obj.Meth, name, type);
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
		factor.struct = Tab.noType;
		if (obj == Tab.noObj) {
			report_error("Designator [" + obj.getName() + "] ne postoji", factor);
			return;
		}
		if (obj.getKind() == Obj.Meth) {
			return;
		}
		factor.struct = obj.getType();
	}

	// da li je argument za len (int|char) (niz|matrica) ?
	public boolean lenParamsOK(Struct param) {
		if (param.getKind() != Struct.Array) {
			return false;
		}

		if (param.getElemType().getKind() == Struct.Array) {
			param = param.getElemType();
		}

		int kind = param.getElemType().getKind();
		return (kind == Struct.Char || kind == Struct.Int);
	}

	public void funcCall(Obj obj, SyntaxNode node) {
		String name = obj.getName();
		report_info("poziv f-je [" + name + ']', node);

		if (obj.getKind() != Obj.Meth) {
			report_error("samo se funkcije mogu pozivati", node);
			return;
		}

		ArrayList<Struct> curr = funStack.pop();
		FunctionData fd = allFunctions.get(name);
		ArrayList<Struct> supposed = fd.arguments;
		if (curr.size() != supposed.size()) {
			String form = "funkcija [%s] ocekuje [%d] parametara a dobila je [%d]";
			report_error(String.format(form, name, fd.parCount, curr.size()), node);
			return;
		}

		for (int i = 0; i < curr.size(); i++) {
			if (name.equals("len")) {
				if (!lenParamsOK(curr.get(i))) {
					String form = "argument f-je [len] mora biti (int|char)(niz|matrica) a dobijen je (%s)";
					report_error(String.format(form, structToString(curr.get(i))), node);
				}
			}
			else if (!curr.get(i).equals(supposed.get(i))) {
				String form = "%d. parametar funkcije [%s] ne odgovara. Ocekuje se tip (%s) a dobijen je tip (%s)";
				report_error(String.format(
						form, i+1, name, structToString(supposed.get(i)), structToString(curr.get(i))), node);
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
		factor.struct = factor.getConstOne().obj.getType();
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
			report_error(String.format("Ime designatora [%s] ne postoji", desig.getName()), desig);
		}
	}

	public void visit(DesignatorScalar desig) {
		desig.obj = desig.getDesignatorName().obj;
	}

	public void visit(DesignatorArray desig) {
		Obj kid = desig.getDesignatorName().obj;
		Struct kidType = kid.getType();
		desig.obj = Tab.noObj;
		if (desig.getExpr().struct != Tab.intType) {
			report_error(String.format("Tip izraza kojim se indeksira promenljiva [%s] mora biti int", desig.obj.getName()), desig);
			return;
		}
		if (kidType.getKind() != Struct.Array) {
			report_error(String.format("Promenljiva [%s] mora biti niz ili matrica", kid.getName()), desig);
			return;
		}

		desig.obj = new Obj(Obj.Elem, kid.getName(), kidType.getElemType());
	}

	public void visit(DesignatorMatrix desig) {
		Obj kid = desig.getDesignatorName().obj;
		Struct kidType = kid.getType();
		desig.obj = Tab.noObj;
		if (desig.getExpr().struct != Tab.intType || desig.getExpr1().struct != Tab.intType) {
			report_error(String.format("Tip izraza kojim se indeksira promenljiva [%s] mora biti int", desig.obj.getName()), desig);
			return;
		}
		if (kidType.getKind() != Struct.Array || kidType.getElemType().getKind() != Struct.Array) {
			report_error(String.format("Promenljiva [%s] mora biti tipa matrica", kid.getName()), desig);
			return;
		}

		desig.obj = new Obj(Obj.Elem, kid.getName(), kidType.getElemType().getElemType());
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

	public void visit(UnmatchedWhile stmt) {
		loopCount--;
	}

	public void visit(MatchedWhile stmt) {
		loopCount--;
	}

	public void visit(WhileStart stmt) {
		loopCount++;
	}

	public void visit(MatchedBreak stmt) {
		if (loopCount < 1) {
			report_error("break iskaz ne sme biti van petlje", stmt);
		}
	}

	public void visit(MatchedContinue stmt) {
		if (loopCount < 1) {
			report_error("continue iskaz ne sme biti van petlje", stmt);
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

	public void mapCheck(Designator desig) {
		Obj obj = desig.obj;
		Struct type = obj.getType();
		if (type.getKind() != Struct.Array ||
			(type.getKind() == Struct.Array && type.getElemType().getKind() == Struct.Array))
		{
			report_error(String.format(
					"promenljiva [%s] mora biti jednodimenzionalni niz ugradjenog tipa u map iskazu",
					obj.getName()), desig);
		}
	}

	public void visit(MatchedMap stmt) {
		Obj obj1 = stmt.getMapWrapper().getDesignator().obj;
		Obj obj2 = stmt.getMapWrapper().getDesignator1().obj;
		Struct type1 = obj1.getType();
		Struct type2 = obj2.getType();
		mapCheck(stmt.getMapWrapper().getDesignator());
		mapCheck(stmt.getMapWrapper().getDesignator1());

		Obj iter = Tab.find(stmt.getIter());
		if (iter == Tab.noObj) {
			report_error(String.format("identifikator [%s] ne postoji", stmt.getIter()), stmt);
		}
		if (!iter.getType().equals(type1.getElemType())) {
			report_error(String.format("tip identifikatora (%s %s) i tip niza (%s %s) se moraju poklapati",
					structToString(iter.getType()), iter.getName(), structToString(type1), obj1.getName()), stmt);
		}
		if (!type1.equals(type2)) {
			report_error(String.format("nizovi (%s %s) i (%s %s) moraju biti nizovi istog tipa",
					structToString(type1), obj1.getName(), structToString(type2), obj2.getName()), stmt);
		}

		mapStatements.put(stmt, new MapData(stmt.getExpr()));
	}
}
