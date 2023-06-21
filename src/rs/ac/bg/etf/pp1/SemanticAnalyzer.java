package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	public static Struct booleanType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();

	Logger log = Logger.getLogger(getClass());

	public boolean errorDetected = false;

	public void report_error(String message, SyntaxNode node) {
		errorDetected = true;
		String err = Colors.ANSI_RED + "Semanticka greska (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_RED + "): ";
		log.error(err + message);
	}

	public void report_info(String message, SyntaxNode node) {
		String err = Colors.ANSI_GREEN + "Info (" +
				Colors.ANSI_RESET +	node.getLine() + Colors.ANSI_GREEN + "): ";
		log.info(err + message);
	}
}
