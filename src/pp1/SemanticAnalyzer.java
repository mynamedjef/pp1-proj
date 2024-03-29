package pp1;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Stack;

import org.apache.log4j.Logger;

import pp1.ast.*;
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

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

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

	HashMap<String, FunctionData> allFunctions = new HashMap<>();
	Stack<ArrayList<Struct>> funStack = new Stack<>();

	private ErrorLogger log = new ErrorLogger("Semantika");
	private Obj currentMethod = null;
	private Struct declarationType = null;
	private Struct returnType = Tab.noType;
	private int loopCount = 0;
	public int nVars = -1;

	public boolean errorDetected() { return log.errorDetected; }

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
	}

	public void visit(Program prog) {
		if (!allFunctions.containsKey("main")) {
			log.report_error("program mora imati funkciju [main]", prog);
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
			log.report_error("konstanta " + name + " je vec deklarisana", constAssign);
			return;
		}

		if (!constAssign.getConstOne().obj.getType().assignableTo(declarationType)) {
			log.report_error("tip konstante [" + name + "] i deklaracija se ne poklapaju", constAssign);
			return;
		}

		Obj obj = Tab.insert(Obj.Con, constAssign.getName(), declarationType);
		Obj kid = constAssign.getConstOne().obj;
		obj.setAdr(kid.getAdr());
		obj.setLevel(0);
		log.report_info("konstanta [" + name + "] definisana", constAssign);
	}

	public void visit(Type type) {
		Obj typeNode = Tab.find(type.getTypeName());
		type.struct = Tab.noType;
		declarationType = Tab.noType;

		if (typeNode == Tab.noObj) {
			log.report_error("Ne postoji tip [" + type.getTypeName() + ']', type);
			return;
		}

		if (typeNode.getKind() != Obj.Type) {
			log.report_error("Ne postoji tip 2 [" + type.getTypeName() + ']', type);
			return;
		}

		declarationType = type.struct = typeNode.getType();
	}

	// -------------------------------------- VarDecl -----------------------------------------------

	public void processVarDecl(VarDeclSingle var, String name, Struct type, String msg) {
		if (Tab.currentScope.findSymbol(name) != null) {
			log.report_error("simbol [" + name + "] vec postoji u trenutnom scope-u", var);
			return;
		}

		Tab.insert(Obj.Var, name, type);
		log.report_info(msg + " [" + name + "] deklarisana", var);
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
			log.report_error("simbol [" + name + "] vec postoji u trenutnom scope-u", var);
			return;
		}

		Tab.insert(Obj.Var, name, type);
		log.report_info("formpar " + msg + " [" + name + "] deklarisan", var);

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
				log.report_error(String.format("metoda [main] mora biti tipa (void)"), method);
			}
			if (arguments.size() > 0) {
				log.report_error("metoda [main] ne sme primati argumente", method);
			}
		}
		if (returnType != type) {
			log.report_error(String.format("tip povratne vrednosti (%s) se ne poklapa sa tipom (%s) funkcije [%s]",
					structToString(returnType), structToString(type), name), method);
		}

		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		currentMethod = null;
		returnType = Tab.noType;
	}

	public void processMethod(MethodTypeAndName method, String name, Struct type) {
		if (Tab.currentScope.findSymbol(name) != null) {
			log.report_error("Metoda [" + name + "] je vec definisana", method);
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

	public void visit(StatementReturnExpr stmt) {
		if (currentMethod == null) {
			log.report_error("return ne moze biti van funkcije", stmt);
			return;
		}

		returnType = stmt.getExpr().struct;
	}

	// -------------------------------------- Expr --------------------------------------------------

	public void visit(ExprAddop expr) {
		if (expr.getExpr().struct != Tab.intType || expr.getTerm().struct != Tab.intType) {
			log.report_error("tokom Addop expr i term moraju biti oba tipa int", expr);
			expr.struct = Tab.noType;
			return;
		}
		expr.struct = Tab.intType;
	}

	public void visit(ExprNegative expr) {
		if (expr.getTerm().struct != Tab.intType) {
			log.report_error("izraz unarnog minusa mora biti int tipa", expr);
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
			log.report_error("tokom Mulop term i factor moraju biti oba tipa int", term);
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
			log.report_error("Designator [" + obj.getName() + "] ne postoji", factor);
			return;
		}
		if (obj.getKind() == Obj.Meth) {
			log.report_error(String.format("Ime funkcije [%s] ne sme samo figurisati u izrazu (mora se pozvati)", obj.getName()), factor);
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
		log.report_info("poziv f-je [" + name + ']', node);

		if (obj.getKind() != Obj.Meth) {
			log.report_error("samo se funkcije mogu pozivati", node);
			return;
		}

		ArrayList<Struct> curr = funStack.pop();
		FunctionData fd = allFunctions.get(name);
		ArrayList<Struct> supposed = fd.arguments;
		if (curr.size() != supposed.size()) {
			String form = "funkcija [%s] ocekuje [%d] parametara a dobila je [%d]";
			log.report_error(String.format(form, name, fd.parCount, curr.size()), node);
			return;
		}

		for (int i = 0; i < curr.size(); i++) {
			if (name.equals("len")) {
				if (!lenParamsOK(curr.get(i))) {
					String form = "argument f-je [len] mora biti (int|char)(niz|matrica) a dobijen je (%s)";
					log.report_error(String.format(form, structToString(curr.get(i))), node);
				}
			}
			else if (!curr.get(i).equals(supposed.get(i))) {
				String form = "%d. parametar funkcije [%s] ne odgovara. Ocekuje se tip (%s) a dobijen je tip (%s)";
				log.report_error(String.format(
						form, i+1, name, structToString(supposed.get(i)), structToString(curr.get(i))), node);
			}
		}
	}

	public void visit(FactorFuncCall factor) {
		Obj obj = factor.getFunctionCall().getFunctionName().getDesignator().obj;
		factor.struct = obj.getType();
		if (obj.getType() == Tab.noType) {
			log.report_error("void funkcija ne moze biti u izrazu", factor);
		}
		funcCall(obj, factor);
	}

	public void visit(FactorConst factor) {
		factor.struct = factor.getConstOne().obj.getType();
	}

	public void visit(FactorNewArray factor) {
		if (factor.getExpr().struct != Tab.intType) {
			log.report_error("Izraz indeksiranja mora biti tipa int", factor);
			factor.struct = Tab.noType;
			return;
		}
		factor.struct = declare_array(factor.getType().struct);
	}

	public void visit(FactorNewMatrix factor) {
		if (factor.getExpr().struct != Tab.intType || factor.getExpr1().struct != Tab.intType) {
			log.report_error("Izraz indeksiranja mora biti tipa int", factor);
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
			log.report_error(String.format("Ime designatora [%s] ne postoji", desig.getName()), desig);
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
			log.report_error(String.format("Tip izraza kojim se indeksira promenljiva [%s] mora biti int", desig.obj.getName()), desig);
			return;
		}
		if (kidType.getKind() != Struct.Array) {
			log.report_error(String.format("Promenljiva [%s] mora biti niz ili matrica", kid.getName()), desig);
			return;
		}

		desig.obj = new Obj(Obj.Elem, kid.getName(), kidType.getElemType());
	}

	public void visit(DesignatorMatrix desig) {
		Obj kid = desig.getDesignatorName().obj;
		Struct kidType = kid.getType();
		desig.obj = Tab.noObj;
		if (desig.getExpr().struct != Tab.intType || desig.getExpr1().struct != Tab.intType) {
			log.report_error(String.format("Tip izraza kojim se indeksira promenljiva [%s] mora biti int", desig.obj.getName()), desig);
			return;
		}
		if (kidType.getKind() != Struct.Array || kidType.getElemType().getKind() != Struct.Array) {
			log.report_error(String.format("Promenljiva [%s] mora biti tipa matrica", kid.getName()), desig);
			return;
		}

		desig.obj = new Obj(Obj.Elem, kid.getName(), kidType.getElemType().getElemType());
	}

	// -------------------------------- DesignatorStatement -----------------------------------------

	public void visit(DesignatorStatementAssign desig) {
		Obj d = desig.getDesignator().obj;
		Struct e = desig.getExpr().struct;

		if (!e.assignableTo(d.getType())) {
			log.report_error("tip promenljive [" + d.getName() + "] se ne poklapa sa tipom dodeljene vrednosti", desig);
		}
		if (d.getKind() != Obj.Var && d.getKind() != Obj.Elem) {
			log.report_error("designator [" + d.getName() + "] mora biti promenljiva ili element niza/matrice", desig);
		}
	}

	public void visit(DesignatorStatementFuncCall desig) {
		Obj obj = desig.getFunctionCall().getFunctionName().getDesignator().obj;
		funcCall(obj, desig);
	}

	public void visit(DesignatorStatementIncr desig) {
		Obj obj = desig.getDesignator().obj;

		if (obj.getType() != Tab.intType || (obj.getKind() != Obj.Var && obj.getKind() != Obj.Elem)) {
			log.report_error("moze se inkrementirati samo integer", desig);
		}
	}

	public void visit(DesignatorStatementDecr desig) {
		Obj obj = desig.getDesignator().obj;

		if (obj.getType() != Tab.intType || (obj.getKind() != Obj.Var && obj.getKind() != Obj.Elem)) {
			log.report_error("moze se dekrementirati samo integer", desig);
		}
	}

	// ----------------------------------- CondFact -------------------------------------------------

	public void visit(CondFactRelop cond) {
		Struct s1 = cond.getExpr().struct;
		Struct s2 = cond.getExpr1().struct;
		if (!s1.compatibleWith(s2)) {
			log.report_error("nekompatibilni tipovi u poredjenju", cond);
			return;
		}
		Relop r = cond.getRelop();
		if (s1.getKind() == Struct.Array && !(r instanceof RelopEQ) && !(r instanceof RelopNEQ)) {
			log.report_error("nizovi mogu da se porede samo sa EQ i NEQ", cond);
		}
	}

	public void visit(CondFactExpr cond) {
		if (cond.getExpr().struct != booleanType) {
			log.report_error("uslov mora biti tipa boolean", cond);
		}
	}

	public void visit(ActPar act) {
		log.report_info("par " + structToString(act.getExpr().struct), act);
		ArrayList<Struct> l = funStack.peek();
		l.add(act.getExpr().struct);
	}

	// ----------------------------------- FunctionCall ---------------------------------------------

	public void visit(FunctionName fun) {
		ArrayList<Struct> l = new ArrayList<>();
		funStack.push(l);
		log.report_info("fun " + fun.getDesignator().obj.getName(), fun);
	}

	// ----------------------------------- Statements -----------------------------------------------

	public void visit(StatementWhile stmt) {
		loopCount--;
	}

	public void visit(WhileStart stmt) {
		loopCount++;
	}

	public void visit(StatementBreak stmt) {
		if (loopCount < 1) {
			log.report_error("break iskaz ne sme biti van petlje", stmt);
		}
	}

	public void visit(StatementContinue stmt) {
		if (loopCount < 1) {
			log.report_error("continue iskaz ne sme biti van petlje", stmt);
		}
	}

	public void visit(StatementPrintExpr stmt) {
		Struct type = stmt.getExpr().struct;
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			log.report_error("print izraz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}

	public void visit(StatementPrintNum stmt) {
		Struct type = stmt.getExpr().struct;
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			log.report_error("print iskaz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}

	public void visit(StatementRead stmt) {
		Obj obj = stmt.getDesignator().obj;
		Struct type = obj.getType();
		if (!type.equals(Tab.charType) && !type.equals(Tab.intType) && !type.equals(booleanType)) {
			log.report_error("read iskaz mora biti [char, bool, int]. dat izraz: (" + structToString(type) + ')', stmt);
		}
	}

	public void mapCheck(Designator desig) {
		Obj obj = desig.obj;
		Struct type = obj.getType();
		if (type.getKind() != Struct.Array ||
			(type.getKind() == Struct.Array && type.getElemType().getKind() == Struct.Array))
		{
			log.report_error(String.format(
					"promenljiva [%s] mora biti jednodimenzionalni niz ugradjenog tipa u map iskazu",
					obj.getName()), desig);
		}
	}

	public void visit(StatementMap stmt) {
		Obj obj1 = stmt.getMapWrapper().getDesignator().obj;
		Obj obj2 = stmt.getMapWrapper().getDesignator1().obj;
		Struct type1 = obj1.getType();
		Struct type2 = obj2.getType();
		mapCheck(stmt.getMapWrapper().getDesignator());
		mapCheck(stmt.getMapWrapper().getDesignator1());

		Obj iter = Tab.find(stmt.getMapWrapper().getIter());
		stmt.getMapWrapper().obj = iter;
		if (iter == Tab.noObj) {
			log.report_error(String.format("identifikator [%s] ne postoji", iter), stmt);
		}
		if (!iter.getType().equals(type1.getElemType())) {
			log.report_error(String.format("tip identifikatora (%s %s) i tip niza (%s %s) se moraju poklapati",
					structToString(iter.getType()), iter.getName(), structToString(type1), obj1.getName()), stmt);
		}
		if (!type1.equals(type2)) {
			log.report_error(String.format("nizovi (%s %s) i (%s %s) moraju biti nizovi istog tipa",
					structToString(type1), obj1.getName(), structToString(type2), obj2.getName()), stmt);
		}
	}
}
