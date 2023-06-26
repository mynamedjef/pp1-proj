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

	// pomocna polja za implementaciju kontrolnih struktura
	Stack<Integer> fixupIf = new Stack<>();
	Stack<Integer> fixupIfEnd = new Stack<>();

	Stack<Integer> fixupWhileCondition = new Stack<>();
	Stack<ArrayList<Integer>> fixupLoopEnd = new Stack<>();
	// -------------------------------------------------------

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

	public void visit(ProgName progName) {
		generateBuiltinFunctionsCode();
	}

	// ------------------------------------------ Const -------------------------------------------

	public void visit(ConstOneNumber cnst) {
		Code.load(cnst.obj);
	}

	public void visit(ConstOneBool cnst) {
		Code.load(cnst.obj);
	}

	public void visit(ConstOneChar cnst) {
		Code.load(cnst.obj);
	}

	// ------------------------------------------- Method -----------------------------------------

	public void visitMethod(MethodTypeAndName method, String name) {
		if (name.equals("main")) {
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

	private int mapStartAdr = -1;
	private int mapEndFixup = -1;

	public void visit(StatementMap stmt) {
		/*
		* ocekuje stek:
		* ..., i, dst, src, dst, i, Expr(src[i])
		 */
		boolean charType = (stmt.getMapWrapper().getDesignator().obj.getType().getElemType().equals(Tab.charType));

		Code.put((charType) ?
				Code.bastore :
				Code.astore);			// ..., i, dst, src				dst[i] = Expr(src[i])
		Code.put(Code.dup_x2);			// ..., src, i, dst, src
		Code.put(Code.pop);				// ..., src, i, dst
		Code.put(Code.dup_x1);			// ..., src, dst, i, dst
		Code.put(Code.arraylength);		// ..., src, dst, i, N
		swapTop();						// ..., src, dst, N, i
		Code.loadConst(1);				// ..., src, dst, N, i, 1
		Code.put(Code.add);				// ..., src, dst, N, i=i+1

		Code.putJump(mapStartAdr);
		Code.fixup(mapEndFixup);
										// ..., src, dst, N, i
		Code.put(Code.pop);				// ..., src, dst, N
		Code.put(Code.pop);				// ..., src, dst
		swapTop();						// ..., dst, src
		Code.put(Code.pop);				// ..., dst
									// ...
									// DST, IDX, ...
		Code.store(stmt.getMapWrapper().getDesignator().obj);
	}

	public void visit(MapWrapper stmt) {
		/*
		 * ocekuje stek:
		 * ..., src
		 * ..., DST, IDX, src
		 */
		boolean charType = (stmt.getDesignator().obj.getType().getElemType().equals(Tab.charType));
		Obj iter = stmt.obj;

		Code.put(Code.dup);				// ..., src, src
		Code.put(Code.arraylength);		// ..., src, N
		Code.put(Code.dup);				// ..., src, N, N
		Code.put(Code.newarray);
		Code.loadConst((charType) ?
				0 : 1);					// ..., src, N, dst
		swapTop();						// ..., src, dst, N
		Code.loadConst(0);				// ..., src, dst, N, i=0
		// FOR:
		mapStartAdr = Code.pc;
		Code.put(Code.dup2);			// ..., src, dst, N, i, N, i
		Code.putFalseJump(Code.gt, 0);	// ..., src, dst, N, i			if (i >= N) jmp END
		mapEndFixup = Code.pc-2;
		swapTop();						// ..., src, dst, i, N
		Code.put(Code.pop);				// ..., src, dst, i
		Code.put(Code.dup_x2);			// ..., i, src, dst, i
		Code.put(Code.dup_x2);			// ..., i, i, src, dst, i
		Code.put(Code.pop);				// ..., i, i, src, dst
		Code.put(Code.dup_x2);			// ..., i, dst, i, src, dst
		Code.put(Code.dup_x2);			// ..., i, dst, dst, i, src, dst
		Code.put(Code.pop);				// ..., i, dst, dst, i, src
		Code.put(Code.dup_x2);			// ..., i, dst, src, dst, i, src
		swapTop();						// ..., i, dst, src, dst, src, i
		Code.put(Code.dup_x1);			// ..., i, dst, src, dst, i, src, i
		Code.put((charType) ?
				Code.baload :
				Code.aload);			// ..., i, dst, src, dst, i, src[i]
		Code.store(iter);				// ..., i, dst, src, dst, i			iter=src[i]

		// Expr code goes here!
	}

	// -------------------------------- Control Structures ----------------------------------------

	public void visit(StatementIf stmt) {
		Code.fixup(fixupIf.pop());
		// statement code goes here
	}

	public void visit(StatementElse stmt) {
		// statement code goes here
		Code.fixup(fixupIfEnd.pop());
	}

	public void visit(StatementWhile stmt) {
		// statement code goes here
		Code.putJump(fixupWhileCondition.pop());
		for (int br : fixupLoopEnd.pop()) {
			Code.fixup(br);
		}
	}

	public void visit(StatementBreak stmt) {
		Code.putJump(0);
		fixupLoopEnd.peek().add(Code.pc-2);
	}

	public void visit(StatementContinue stmt) {
		Code.putJump(fixupWhileCondition.peek());
	}

	// proverava da li je vrednost condition-a jednaka 1 ili 0, i na osnovu toga skace (ili ne)
	public void visit(IfStart ifstart) {
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0);		// if (!(cond==1)) jmp end
		fixupIf.push(Code.pc-2);
		// statement code goes here
	}

	public void visit(ElseStart elsestart) {
		// statement code goes here
		Code.putJump(0);
		fixupIfEnd.push(Code.pc-2);
		Code.fixup(fixupIf.pop());
	}

	public void visit(WhileCondition whilecondition) {
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0);
		fixupLoopEnd.peek().add(Code.pc-2);
		// statement code goes here
	}

	public void visit(WhileStart whilestart) {
		fixupWhileCondition.push(Code.pc);
		fixupLoopEnd.push(new ArrayList<>());
	}

	// --------------------------------------- Condition ------------------------------------------

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
		Obj obj = func.getFunctionName().getDesignator().obj;
		int offset = obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	// ------------------------------------------ Expr --------------------------------------------

	public void visit(ExprAddop expr) {
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
		Code.put(Code.neg);
	}

	public void visit(ExprTerm expr) { /* empty: Term se propagira na gore */ }

	// ------------------------------------------ Term --------------------------------------------

	public void visit(TermMulop term) {
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

		Code.put(Code.newarray);
		Code.put(factor.struct.equals(Tab.charType) ? 0 : 1);
	}

	public void swapTop() {
		// obrće gornje dve vrednosti steka
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
	}

	public void visit(FactorNewMatrix factor) {
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

		if (!(desig.getParent() instanceof DesignatorScalar)) {
			Code.load(desig.obj);
		}
	}

	public void visit(DesignatorScalar desig) {
		SyntaxNode parent = desig.getParent();

		// trenutni stek: ..., var
		if (parent instanceof StatementRead) {
			// ocekuje se stek: ...
		}
		else if (parent instanceof MapWrapper) {
			// ocekuje se stek: ..., arrptr
			if (!mapDest) { // za destinaciju ne stavljamo nista, a za source stavljamo pokazivac na niz
				Code.load(desig.obj);
			}
			mapDest ^= true;
		}
		else if (parent instanceof DesignatorStatementIncr || parent instanceof DesignatorStatementDecr) {
			// ocekuje se stek: ...
		}
		else if (parent instanceof DesignatorStatementAssign) {
			// ocekuje se stek: ...
		}
		else if (parent instanceof FunctionName) {
			// ocekuje se stek: ...
			// nije potrebno nista na steku ostavljati jer FunctionCall
			// poziva funkciju na osnovu objekta
		}
		else if (parent instanceof FactorDesignator) {
			// ocekuje se stek: ..., rval
			Code.load(desig.obj);
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
		// trenutni stek: ..., arr, idx
		processDereferencing(desig, desig.getDesignatorName().obj);
	}

	public void visit(DesignatorMatrix desig) {
		// trenutni stek: ..., mat, idx1, idx2
		Code.put(Code.dup_x2);	// ..., idx2, mat, idx1, idx2
		Code.put(Code.pop);		// ..., idx2, mat, idx1
		Code.load(desig.obj);	// ..., idx2, mat[idx1]
		swapTop();				// ..., mat[idx1], idx2

		processDereferencing(desig, desig.getDesignatorName().obj);
	}
}
