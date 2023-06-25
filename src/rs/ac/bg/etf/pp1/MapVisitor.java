package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class MapVisitor extends CodeGenerator {

	public MapVisitor(SemanticAnalyzer sem) {
		super(sem);
	}

	// ------------------------------------------ Const -------------------------------------------

	/* INHERITED
	public void visit(ConstOneNumber cnst) { }

	public void visit(ConstOneBool cnst) { }

	public void visit(ConstOneChar cnst) { }
	*/

	// ------------------------------------------ Expr --------------------------------------------

	/* INHERITED
	public void visit(ExprAddop expr) { }

	public void visit(ExprNegative expr) {}

	public void visit(ExprTerm expr) { }
	*/

	// ------------------------------------------ Term --------------------------------------------

	/* INHERITED
	public void visit(TermMulop term) { }

	public void visit(TermFactor term) { }
	*/

	// -------------------------------------- Factor ----------------------------------------------

	/* INHERITED
	public void visit(FactorDesignator factor) { }

	public void visit(FactorFuncCall factor) { }

	public void visit(FactorConst cnst) { }

	public void visit(FactorNewArray factor) { }

	public void swapTop() { }

	public void visit(FactorNewMatrix factor) {	}

	public void visit(FactorExpr factor) {  }
	*/

	// ------------------------------------ Designator --------------------------------------------

	public void visit(DesignatorName desig) {
		// empty!
	}

	@Override
	public void visit(DesignatorScalar desig) {
		Code.load(SemanticAnalyzer.mapIterator);
	}

	@Override
	public void visit(DesignatorArray desig) {
		Code.load(SemanticAnalyzer.mapIterator);
	}

	@Override
	public void visit(DesignatorMatrix desig) {
		Code.load(SemanticAnalyzer.mapIterator);
	}

	@Override
	public void visit(FunctionCall func) {
		Obj obj = func.getFunctionName().getDesignator().obj;
		//sem.report_info(obj.getName(), func);
		//sem.report_info(Integer.toString(obj.getAdr()), func);

		int offset = obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	@Override
	public void visit(FunctionName func) {
		// ne ostavljamo ime designatora na steku ako zovemo funkciju
		Code.put(Code.pop);
	}
}
