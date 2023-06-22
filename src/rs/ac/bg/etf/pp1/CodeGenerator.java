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
		Code.put(method.obj.getLevel());
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

	public void visit(MatchedPrintExpr print) {
		if (print.getExpr().struct.equals(Tab.charType)) {
			Code.loadConst(1);
			Code.put(Code.bprint);
		} else {
			Code.loadConst(5);
			Code.put(Code.print);
		}
	}

	public void visit(MatchedReturnExpr matched) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(MatchedReturnVoid matched) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	// ---------------------------------- DesignatorStatement -------------------------------------

	public void visit(DesignatorStatementAssign desig) {
		Code.store(desig.getDesignator().obj);
	}

	public void visit(DesignatorStatementFuncCall desig) {
		// u slucaju da zovemo funkciju koja vraca vrednost, a ne koristimo tu vrednost nigde u izrazu,
		// popujemo vrednost sa steka
		if (desig.getFunctionCall().getFunctionName().getDesignator().obj.getType() != Tab.noType) {
			Code.put(Code.pop);
		}
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

	// -------------------------------------- Factor ----------------------------------------------

	public void visit(FactorDesignator factor) {
		Code.load(factor.getDesignator().obj);
	}

	public void visit(FactorNewArray factor) {
		Code.put(Code.newarray);
		Code.put(factor.struct.equals(Tab.charType) ? 0 : 1);
	}

	public void visit(FactorNewMatrix factor) {
		// TODO
	}
}
