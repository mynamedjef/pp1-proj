package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.ArrayList;

import rs.ac.bg.etf.pp1.SemanticAnalyzer.FunctionData;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;

	public int getMainPc() { return mainPc; }

	SemanticAnalyzer sem;

	public CodeGenerator(SemanticAnalyzer sem) { this.sem = sem; }

	public boolean mapDest = true;

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
		for (MatchedMap m : sem.mapStatements.keySet()) {
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

	public void visit(MatchedReturnExpr matched) {
		// + na steku je rezultat Expr-a
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(MatchedReturnVoid matched) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(MatchedRead stmt) {
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

	public void visit(MatchedPrintExpr print) {
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

	public void visit(MatchedPrintNum stmt) {
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

	public void visit(MatchedMap stmt) {
		/*
		 * ocekuje ste stek:
		 * 	..., src, expr
		 *  ..., DST, IDX, src, expr
		 *  trenutni stek ce se ignorisati tokom ispisa
		 */
		boolean charType = (stmt.getDesignator().obj.getType().getElemType().equals(Tab.charType));
		Obj dst = stmt.getDesignator().obj;

										// ..., src, expr
		Code.put(Code.pop);				// ..., src
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
		 * 1. ako se u Expr zove funkcija sa bocnim efektom, to ce biti problem jer se poziv funkcije koja je u
		 * Expr izvrsi "u prazno" tj. njegov rezultat se odmah popuje sa steka.
		 * 2. drugi problem je ako se uradi arr.map(x => x+y): ovo je problem jer za sad map podrzava samo koriscenje
		 * jedne promenljive u izrazu
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

	// --------------------------------------- Condition ------------------------------------------

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
			sem.report_error("FATAL: unreachable branch", expr);
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
			sem.report_error("FATAL: unreachable branch", term);
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

	public void visit(DesignatorName desig) { /* empty */ }

	public void visit(DesignatorScalar desig) {
		SyntaxNode parent = desig.getParent();

		// trenutni stek: ...
		if (parent instanceof MatchedRead) {
			// ocekuje se stek: ...
		}
		else if (parent instanceof MatchedMap) {
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
			sem.report_error("FATAL: unreachable branch", desig);
		}
	}

	public void visit(DesignatorArray desig) {
		SyntaxNode parent = desig.getParent();
		Obj elemObj = desig.getDesignatorName().obj;

		// trenutni stek:   ..., idx
		if (parent instanceof MatchedRead) {
			// ocekuje se stek: ..., arr, idx
			Code.load(elemObj);
			swapTop();
		}
		else if (parent instanceof MatchedMap) {
			// ocekuje se stek: ... dst, idx, src
			if (mapDest) { // za destinaciju samo pravimo da stek bude: ..., dst, idx
				Code.load(elemObj);
				swapTop();
			} else {		// za source samo pravimo da stek bude: ..., src[idx]
				Code.load(elemObj);
				swapTop();
				Code.load(desig.obj);
			}
			mapDest ^= true;
		}
		else if (parent instanceof DesignatorStatementIncr || parent instanceof DesignatorStatementDecr) {
			// ocekuje se stek: ..., arr, idx, arr, idx
			Code.load(elemObj);
			swapTop();
			Code.put(Code.dup2);
		}
		else if (parent instanceof DesignatorStatementAssign) {
			// ocekuje se stek: ..., arr, idx
			Code.load(elemObj);
			swapTop();
		}
		else if (parent instanceof FunctionName) {
			// ocekuje se stek: ...
			// nije potrebno nista na steku ostavljati jer FunctionCall
			// poziva funkciju na osnovu objekta
		}
		else if (parent instanceof FactorDesignator) {
			// ocekuje se stek: ..., arr[idx]
			Code.load(elemObj);
			swapTop();
			Code.load(desig.obj);
		}
		else {
			sem.report_error("FATAL: unreachable branch", desig);
		}
	}

	public void visit(DesignatorMatrix desig) {
		SyntaxNode parent = desig.getParent();
		Obj elemObj = desig.getDesignatorName().obj;

		// trenutni stek: ..., idx1, idx2
		if (parent instanceof MatchedRead) {
			// ocekuje se stek: ..., mat[idx1], idx2
			swapTop();				// ..., idx2, idx1
			Code.load(elemObj);		// ..., idx2, idx1, mat
			swapTop();				// ..., idx2, mat, idx1
			Code.load(desig.obj);	// ..., idx2, mat[idx1]
			swapTop();				// ..., mat[idx1], idx2
		}
		else if (parent instanceof MatchedMap) {
			// ovde nikad ne bi smelo da se udje
			sem.report_error("FATAL: unreachable branch", desig);
		}
		else if (parent instanceof DesignatorStatementIncr || parent instanceof DesignatorStatementDecr) {
			// ocekuje se stek: ..., mat[idx1], idx2, mat[idx1], idx2
			swapTop();				// ..., idx2, idx1
			Code.load(elemObj);		// ..., idx2, idx1, mat
			swapTop();				// ..., idx2, mat, idx1
			Code.load(desig.obj);	// ..., idx2, mat[idx1]
			swapTop();				// ..., mat[idx1], idx2
			Code.put(Code.dup2);	// ..., mat[idx1], idx2, mat[idx1], idx2
		}
		else if (parent instanceof DesignatorStatementAssign) {
			// ocekuje se stek: ..., mat[idx1], idx2
			swapTop();				// ..., idx2, idx1
			Code.load(elemObj);		// ..., idx2, idx1, mat
			swapTop();				// ..., idx2, mat, idx1
			Code.load(desig.obj);	// ..., idx2, mat[idx1]
			swapTop();				// ..., mat[idx1], idx2
		}
		else if (parent instanceof FunctionName) {
			// ocekuje se stek: ...
			// nije potrebno nista na steku ostavljati jer FunctionCall
			// poziva funkciju na osnovu objekta
		}
		else if (parent instanceof FactorDesignator) {
			// ocekuje se stek: ..., mat[idx1][idx2]
			swapTop();				// ..., idx2, idx1
			Code.load(elemObj);		// ..., idx2, idx1, mat
			swapTop();				// ..., idx2, mat, idx1
			Code.load(desig.obj);	// ..., idx2, mat[idx1]
			swapTop();				// ..., mat[idx1], idx2
			Code.load(desig.obj);	// ..., mat[idx1][idx2]
		}
		else {
			sem.report_error("FATAL: unreachable branch", desig);
		}
	}
}
