package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

	Logger log = Logger.getLogger(getClass());

	public boolean errorDetected = false;

	private Struct declarationType = null;
	private int nVars = -1;

	public void report_error(String message, SyntaxNode node) {
		errorDetected = true;
		String err = Colors.ANSI_RED + "Semanticka greska (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_RED + "): ";
		log.error(err + message + Colors.ANSI_RESET);
	}

	public void report_info(String message, SyntaxNode node) {
		String err = Colors.ANSI_GREEN + "Info (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_GREEN + "): ";
		log.info(err + message + Colors.ANSI_RESET);
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
}
