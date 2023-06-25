package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Stack;

import rs.ac.bg.etf.pp1.SemanticAnalyzer.FunctionData;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;

	private ErrorLogger log = new ErrorLogger("Generisanje");

	public int getMainPc() { return mainPc; }

	SemanticAnalyzer sem;

	public CodeGenerator(SemanticAnalyzer sem) { this.sem = sem; }

	public boolean mapDest = true;

	/*
	 * Map iskaz se zadaje sa Expr iskazom. S obzirom da se svi Expr iskazi automatski racunaju
	 * na steku, moguc je slucaj arr.map(x => f(x)), gde ako f(x) ima bocni efekat, bocni efekat
	 * ce se javiti pre nego sto uopste pocne iteracija po arr. Ovaj flag sluzi da spreci tu situaciju
	 */
	public boolean mapExpr = false;

	public boolean errorDetected() { return log.errorDetected; }

	// ======================================== VISITI =============================================

	public void generateBuiltinFunctionsCode() {
		Tab.find("chr").setAdr(Code.pc);
		Tab.find("ord").setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1); Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

		Tab.find("len").setAdr(0);
		Code.put(Code.enter);
		Code.put(1); Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void generateMapFunctions() {
		for (StatementMap m : sem.mapStatements.keySet()) {
			SemanticAnalyzer.MapData md = sem.mapStatements.get(m);
			Expr e = md.e;

			md.setAdr(Code.pc);
			Code.put(Code.enter); Code.put(0); Code.put(0);

			MapVisitor temp = new MapVisitor(sem);
			e.traverseBottomUp(temp);

			Code.put(Code.exit);
			Code.put(Code.return_);
		}
	}

	public void visit(ProgName progName) {
		generateBuiltinFunctionsCode();
	}

	// ------------------------------------------ Const -------------------------------------------

	public void visit(ConstOneNumber cnst) {
		if (mapExpr) {
			return;
		}
		Code.load(cnst.obj);
	}

	public void visit(ConstOneBool cnst) {
		if (mapExpr) {
			return;
		}
		Code.load(cnst.obj);
	}

	public void visit(ConstOneChar cnst) {
		if (mapExpr) {
			return;
		}
		Code.load(cnst.obj);
	}

	// ------------------------------------------- Method -----------------------------------------

	public void visitMethod(MethodTypeAndName method, String name) {
		if (name.equals("main")) {
			generateMapFunctions();
			mainPc = Code.pc;
		}
		method.obj.setAdr(Code.pc);

		Code.put(Code.enter);
		Code.put(sem.allFunctions.get(method.obj.getName()).parCount);
		Code.put(method.obj.getLocalSymbols().size());
	}

	public void visit(MethodTypeAndNameNonvoid method) {
		visitMethod(method, method.getName());
	}

	public void visit(MethodTypeAndNameVoid method) {
		visitMethod(method, method.getName());
	}

	public void visit(MethodDecl method) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	// ---------------------------------------- Statement -----------------------------------------

	public void visit(StatementReturnExpr stmt) {
		// + na steku je rezultat Expr-a
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(StatementReturnVoid stmt) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(StatementRead stmt) {
		/*
		 * ocekuje se jedan od ova dva steka:
		 * 	...
		 *  ..., arr, idx
		 */
		Obj obj = stmt.getDesignator().obj;
		if (obj.getType().equals(Tab.charType)) {
			Code.put(Code.bread);
		}
		else {
			Code.put(Code.read);
		}
		Code.store(obj);
	}

	public void visit(StatementPrintExpr print) {
		/*
		 * ocekuje stek: ..., val
		 */
		if (print.getExpr().struct.equals(Tab.charType)) {
			Code.loadConst(1);
			Code.put(Code.bprint);
		} else {
			Code.loadConst(5);
			Code.put(Code.print);
		}
	}

	public void visit(StatementPrintNum stmt) {
		/*
		 * ocekuje stek: ..., val
		 */
		Code.loadConst(stmt.getN2());
		if (stmt.getExpr().struct.equals(Tab.charType)) {
			Code.put(Code.bprint);
		}
		else {
			Code.put(Code.print);
		}
	}

	public void visit(StatementMap stmt) {
		/*
		 * ocekuje ste stek:
		 * 	..., src
		 *  ..., DST, IDX, src
		 *  trenutni stek ce se ignorisati tokom ispisa
		 */
		mapExpr = false;
		boolean charType = (stmt.getMapWrapper().getDesignator().obj.getType().getElemType().equals(Tab.charType));
		Obj dst = stmt.getMapWrapper().getDesignator().obj;

										// ..., src
		Code.put(Code.dup);				// ..., src, src
		Code.put(Code.arraylength);		// ..., src, N
		Code.put(Code.newarray);
		Code.put(charType ? 0 : 1); 	// ..., src, dst									dst = new arr[len(src)]
		Code.loadConst(0);				// ..., src, dst, i=0								i = 0

		int forPc = Code.pc;
		// FOR:
		swapTop();						// ..., src, i, dst
		Code.put(Code.dup_x2);			// ..., dst, src, i, dst
		Code.put(Code.arraylength); 	// ..., dst, src, i, N
		Code.put(Code.dup2);			// ..., dst, src, i, N, i, N

		Code.putFalseJump(Code.lt, 0);	// ..., dst, src, i, N								if (N <= i) goto END
		int falseJump = Code.pc-2;

		Code.put(Code.pop);				// ..., dst, src, i
		Code.put(Code.dup_x2);			// ..., i, dst, src, i
		Code.put(Code.dup_x2);			// ..., i, i, dst, src, i
		Code.put(Code.pop);				// ..., i, i, dst, src
		Code.put(Code.dup_x2);			// ..., i, src, i, dst, src
		Code.put(Code.dup_x2);			// ..., i, src, src, i, dst, src
		Code.put(Code.pop);				// ..., i, src, src, i, dst
		Code.put(Code.dup_x2);			// ..., i, src, dst, src, i, dst
		Code.put(Code.dup_x2);			// ..., i, src, dst, dst, src, i, dst
		Code.put(Code.pop);				// ..., i, src, dst, dst, src, i
		Code.put(Code.dup_x1);			// ..., i, src, dst, dst, i, src, i
		Code.put(charType ?
				Code.baload :
				Code.aload);			// ..., i, src, dst, dst, i, src[i]
		Code.store(SemanticAnalyzer.mapIterator);
										// ..., i, src, dst, dst, i

		SemanticAnalyzer.MapData md = sem.mapStatements.get(stmt);
		int offset = md.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);				// ..., i, src, dst, dst, i, e(src[i])
		/*
		 * TODO:
		 * 1. izraz arr.map(x => x+y) nije sintaksicki neispravan. ovo je problem jer za sad map podrzava samo
		 * koriscenje jedne promenljive u izrazu.
		 */

		Code.put(charType ?
				Code.bastore :
				Code.astore);			// ..., i, src, dst									dst[i] = expr(src[i])
		swapTop();						// ..., i, dst, src
		Code.put(Code.dup_x2);			// ..., src, i, dst, src
		Code.put(Code.pop);				// ..., src, i, dst
		swapTop();						// ..., src, dst, i
		Code.put(Code.const_1);			// ..., src, dst, i, 1
		Code.put(Code.add);				// ..., src, dst, i=i+1								i++;

		Code.putJump(forPc);			//													goto FOR
		Code.fixup(falseJump);
		// END:
										// ..., dst, src, i, N
		Code.put(Code.pop);				// ..., dst, src, i
		Code.put(Code.pop);				// ..., dst, src
		Code.put(Code.pop);				// ..., dst
								// ..., dst
								// ILI
								// ..., DST, IDX, dst
		Code.store(dst);
	}

	public void visit(MapWrapper stmt) {
		mapExpr = true;
	}

	// --------------------------------------- Condition ------------------------------------------

	Stack<Integer> fixupIf = new Stack<>();
	Stack<Integer> fixupIfEnd = new Stack<>();

	public void visit(StatementIf stmt) {
		Code.fixup(fixupIf.pop());
		// statement code goes here
	}

	public void visit(StatementElse stmt) {
		// statement code goes here
		Code.fixup(fixupIfEnd.pop());
	}

	// proverava da li je vrednost condition-a jednaka 1 ili 0, i na osnovu toga skace (ili ne)
	public void visit(IfStart ifstart) {
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0);		// if (!(cond==1)) jmp end
		fixupIf.add(Code.pc-2);
		// statement code goes here
	}

	public void visit(ElseStart elsestart) {
		// statement code goes here
		Code.putJump(0);
		fixupIfEnd.push(Code.pc-2);
		Code.fixup(fixupIf.pop());
	}

	// ostavlja vrednost 1 na steku u slucaju da je jedna od prethodne dve vrednosti True inace vrednost 0
	public void visit(ConditionOr cond) {
		Code.put(Code.add);
		Code.loadConst(0);
		Code.putFalseJump(Code.gt, 0);
		int adrIf = Code.pc-2;
		Code.loadConst(1);
		Code.putJump(0);
		int adrElse = Code.pc-2;
		// ELSE
		Code.fixup(adrIf);
		Code.loadConst(0);
		// END
		Code.fixup(adrElse);
	}

	// ostavlja vrednost 1 na steku u slucaju da su prethodna dve vrednosti True inace vrednost 0
	public void visit(CondTermAnd cond) {
		Code.put(Code.mul);
	}

	public int getRelopCode(Relop rel) {
		if (rel instanceof RelopEQ) {
			return Code.eq;
		} else if (rel instanceof RelopNEQ) {
			return Code.ne;
		} else if (rel instanceof RelopGT) {
			return Code.gt;
		} else if (rel instanceof RelopGE) {
			return Code.ge;
		} else if (rel instanceof RelopLT) {
			return Code.lt;
		} else if (rel instanceof RelopLE) {
			return Code.le;
		} else {
			log.report_error("Relop unreachable branch", rel);
			return Code.eq;
		}
	}

	// ostavlja vrednost 1 na steku u slucaju da je relop ispunjen inace vrednost 0
	public void visit(CondFactRelop cond) {
		Code.putFalseJump(getRelopCode(cond.getRelop()), 0);	// if (!(x relop y)) jmp else
		int adrIf = Code.pc-2;
		Code.loadConst(1);										// push 1
		Code.putJump(0);										// jmp end
		int adrElse = Code.pc-2;
		// ELSE
		Code.fixup(adrIf);
		Code.loadConst(0);										// push 0
		// END
		Code.fixup(adrElse);
	}

	// ---------------------------------- DesignatorStatement -------------------------------------

	public void visit(DesignatorStatementAssign desig) {
		/*
		 * ocekuje se jedan od ova dva steka:
		 * 	..., expr
		 *  ..., arr, idx, expr
		 */
		Code.store(desig.getDesignator().obj);
	}

	public void visit(DesignatorStatementFuncCall desig) {
		// u slucaju da zovemo funkciju koja vraca vrednost, a ne koristimo tu vrednost nigde u izrazu,
		// popujemo vrednost sa steka
		if (desig.getFunctionCall().getFunctionName().getDesignator().obj.getType() != Tab.noType) {
			Code.put(Code.pop);
		}
	}

	public void processCrementation(Obj obj, boolean incr) {
		/*
		 * ocekuje se jedan od ova dva steka:
		 * 	...
		 *  ..., arr, idx
		 */
		Code.load(obj);
		Code.put((incr) ? Code.const_1 : Code.const_m1);
		Code.put(Code.add);
		Code.store(obj);
	}

	public void visit(DesignatorStatementIncr stmt) {
		processCrementation(stmt.getDesignator().obj, true);
	}

	public void visit(DesignatorStatementDecr stmt) {
		processCrementation(stmt.getDesignator().obj, false);
	}

	public void visit(FunctionCall func) {
		if (mapExpr) {
			return;
		}

		Obj obj = func.getFunctionName().getDesignator().obj;
		int offset = obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	// ------------------------------------------ Expr --------------------------------------------

	public void visit(ExprAddop expr) {
		if (mapExpr) {
			return;
		}

		Addop op = expr.getAddop();
		if (op instanceof AddopPlus) {
			Code.put(Code.add);
		}
		else if (op instanceof AddopMinus) {
			Code.put(Code.sub);
		}
		else {
			log.report_error("FATAL: unreachable branch", expr);
		}
	}

	public void visit(ExprNegative expr) {
		if (mapExpr) {
			return;
		}
		Code.put(Code.neg);
	}

	public void visit(ExprTerm expr) { /* empty: Term se propagira na gore */ }

	// ------------------------------------------ Term --------------------------------------------

	public void visit(TermMulop term) {
		if (mapExpr) {
			return;
		}

		Mulop op = term.getMulop();
		if (op instanceof MulopMul) {
			Code.put(Code.mul);
		}
		else if (op instanceof MulopDiv) {
			Code.put(Code.div);
		}
		else if (op instanceof MulopMod) {
			Code.put(Code.rem);
		}
		else {
			log.report_error("FATAL: unreachable branch", term);
		}
	}

	public void visit(TermFactor term) { /* empty: Factor se propagira na gore */ }

	// -------------------------------------- Factor ----------------------------------------------

	public void visit(FactorDesignator factor) { /* empty */ }

	public void visit(FactorFuncCall factor) { /* empty: FunctionCall je nonvoid i propagira na gore */ }

	public void visit(FactorConst cnst) { /* empty: ConstOne se propagira na gore */ }

	public void visit(FactorNewArray factor) {
		/*
		 * ocekuje stek: ..., n
		 */
		if (mapExpr) {
			return;
		}

		Code.put(Code.newarray);
		Code.put(factor.struct.equals(Tab.charType) ? 0 : 1);
	}

	public void swapTop() {
		// obrće gornje dve vrednosti steka
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
	}

	public void visit(FactorNewMatrix factor) {
		if (mapExpr) {
			return;
		}

		boolean charType = factor.struct.equals(Tab.charType);

		// mat[e1][e2], e1 je broj vrsta a e2 broj kolona   STEK
		//													..., e1, e2
		swapTop(); //										..., e2, e1
		Code.put(Code.dup); //                              ..., e2, e1, e1
		Code.put(Code.newarray);
		Code.put(1); //										..., e2, e1, mat				mat = new array[e1];
		swapTop(); //										..., e2, mat, e1
		Code.put(Code.const_1); //							..., e2, mat, e1, 1
		Code.put(Code.sub); //								..., e2, mat, e1				e1--;

		// FOR:
		int forPc = Code.pc; // 						FOR:
		Code.put(Code.dup); //								..., e2, mat, e1, e1
		Code.loadConst(0); //								..., e2, mat, e1, e1, 0
		Code.putFalseJump(Code.ge, 0); //					..., e2, mat, e1				if (0 >= e1) jmp END;
		int falseJump = Code.pc-2;

		Code.put(Code.dup_x2); //							..., e1, e2, mat, e1
		Code.put(Code.dup_x2); //							..., e1, e1, e2, mat, e1
		Code.put(Code.pop); //								..., e1, e1, e2, mat
		Code.put(Code.dup_x2); //							..., e1, mat, e1, e2, mat
		Code.put(Code.dup_x2); //							..., e1, mat, mat, e1, e2, mat
		Code.put(Code.pop); //								..., e1, mat, mat, e1, e2
		Code.put(Code.newarray);
		Code.put(charType ? 0 : 1); //						..., e1, mat, mat, e1, arr		arr = new array[e2];
		Code.put(Code.dup_x2); //							..., e1, mat, arr, mat, e1, arr
		Code.put(charType ? Code.bastore : Code.astore); // ..., e1, mat, arr				mat[e1] = arr;
		Code.put(Code.arraylength); //						..., e1, mat, e2

		Code.put(Code.dup_x2); //							..., e2, e1, mat, e2
		Code.put(Code.pop); //								..., e2, e1, mat
		Code.put(Code.dup_x1); //							..., e2, mat, e1, mat
		Code.put(Code.pop); //								..., e2, mat, e1

		Code.put(Code.const_1); //							..., e2, mat, e1, 1
		Code.put(Code.sub); //								..., e2, mat, e1				e1--;
		Code.putJump(forPc); //								..., e2, mat, e1				jmp FOR
		Code.fixup(falseJump);

		// END:											END:
		Code.put(Code.pop); //								..., e2, mat
		swapTop(); //										..., mat, e2
		Code.put(Code.pop); //								..., mat

		/*
		  [START]	  ..., e1, e2
		  swap        ..., e2, e1
		  dup         ..., e2, e1, e1
		  newarray 1  ..., e2, e1, mat
		  swap        ..., e2, mat, e1
		  const_1     ..., e2, mat, e1, 1
		  sub         ..., e2, mat, e1=e1-1
for:
		  dup         ..., e2, mat, e1, e1
		  const_0     ..., e2, mat, e1, e1, 0
		  jlt [END]   ..., e2, mat, e1

		  dup_x2      ..., e1, e2, mat, e1
		  dup_x2      ..., e1, e1, e2, mat, e1
		  pop         ..., e1, e1, e2, mat
		  dup_x2      ..., e1, mat, e1, e2, mat
		  dup_x2      ..., e1, mat, mat, e1, e2, mat
		  pop         ..., e1, mat, mat, e1, e2
		  newarray ?  ..., e1, mat, mat, e1, arr
		  dup_x2      ..., e1, mat, arr, mat, e1, arr
		  bastore     ..., e1, mat, arr
		  arraylen    ..., e1, mat, e2

		  dup_x2      ..., e2, e1, mat, e2
		  pop         ..., e2, e1, mat
		  dup_x1      ..., e2, mat, e1, mat
		  pop         ..., e2, mat, e1

		  const_1     ..., e2, mat, e1, 1
		  sub         ..., e2, mat, e1=e1-1
		  jmp [FOR]   ..., e2, mat, e1
end:
		  pop         ..., e2, mat
		  swap        ..., mat, e2
		  pop         ..., mat
		 */
	}

	public void visit(FactorExpr factor) { /* empty: Expr se propagira na gore */ }

	// ------------------------------------ Designator --------------------------------------------

	public void visit(DesignatorName desig) {
		if (mapExpr) {
			return;
		}

		Code.load(desig.obj);
	}

	public void visit(DesignatorScalar desig) {
		if (mapExpr) {
			return;
		}

		SyntaxNode parent = desig.getParent();

		// trenutni stek: ..., var
		if (parent instanceof StatementRead) {
			// ocekuje se stek: ...
			Code.put(Code.pop);
		}
		else if (parent instanceof MapWrapper) {
			// ocekuje se stek: ..., arrptr
			if (mapDest) { // za destinaciju ne stavljamo nista, a za source stavljamo pokazivac na niz
				Code.put(Code.pop);
			}
			mapDest ^= true;
		}
		else if (parent instanceof DesignatorStatementIncr || parent instanceof DesignatorStatementDecr) {
			// ocekuje se stek: ...
			Code.put(Code.pop);
		}
		else if (parent instanceof DesignatorStatementAssign) {
			// ocekuje se stek: ...
			Code.put(Code.pop);
		}
		else if (parent instanceof FunctionName) {
			// ocekuje se stek: ...
			// nije potrebno nista na steku ostavljati jer FunctionCall
			// poziva funkciju na osnovu objekta
			Code.put(Code.pop);
		}
		else if (parent instanceof FactorDesignator) {
			// ocekuje se stek: ..., rval
		}
		else {
			log.report_error("FATAL: unreachable branch", desig);
		}
	}

	public void processDereferencing(Designator desig, Obj elemObj) {
		SyntaxNode parent = desig.getParent();

		// trenutni stek:   ..., arr, idx
		if (parent instanceof StatementRead) {
			// ocekuje se stek: ..., arr, idx
		}
		else if (parent instanceof MapWrapper) {
			// ocekuje se stek: ... dst, idx, src
			if (mapDest) { // za destinaciju samo pravimo da stek bude: ..., dst, idx
				// empty
			} else {		// za source samo pravimo da stek bude: ..., src[idx]
				Code.load(desig.obj);
			}
			mapDest ^= true;
		}
		else if (parent instanceof DesignatorStatementIncr || parent instanceof DesignatorStatementDecr) {
			// ocekuje se stek: ..., arr, idx, arr, idx
			Code.put(Code.dup2);
		}
		else if (parent instanceof DesignatorStatementAssign) {
			// ocekuje se stek: ..., arr, idx
		}
		else if (parent instanceof FunctionName) {
			// ocekuje se stek: ...
			// nije potrebno nista na steku ostavljati jer FunctionCall
			// poziva funkciju na osnovu objekta
			Code.put(Code.pop);
			Code.put(Code.pop);
		}
		else if (parent instanceof FactorDesignator) {
			// ocekuje se stek: ..., arr[idx]
			Code.load(desig.obj);
		}
		else {
			log.report_error("FATAL: unreachable branch", desig);
		}
	}

	public void visit(DesignatorArray desig) {
		if (mapExpr) {
			return;
		}

		// trenutni stek: ..., arr, idx
		processDereferencing(desig, desig.getDesignatorName().obj);
	}

	public void visit(DesignatorMatrix desig) {
		if (mapExpr) {
			return;
		}

		// trenutni stek: ..., mat, idx1, idx2
		Code.put(Code.dup_x2);	// ..., idx2, mat, idx1, idx2
		Code.put(Code.pop);		// ..., idx2, mat, idx1
		Code.load(desig.obj);	// ..., idx2, mat[idx1]
		swapTop();				// ..., mat[idx1], idx2

		processDereferencing(desig, desig.getDesignatorName().obj);
	}
}
