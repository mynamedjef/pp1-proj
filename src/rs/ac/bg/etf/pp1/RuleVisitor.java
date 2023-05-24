package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
//import rs.ac.bg.etf.pp1.ast.DesignatorStatement;
//import rs.ac.bg.etf.pp1.ast.VarDecl;
//import rs.ac.bg.etf.pp1.ast.StmtPrint;
import org.apache.log4j.*;

public class RuleVisitor extends VisitorAdaptor {

	Logger log = Logger.getLogger(getClass());

	public int printCallCount = 0;
	public int varDeclCount = 0;

	/*
	public void visit(VarDecl varDecl)
	{
		varDeclCount++;
	}

	public void visit(StmtPrint PrintStmt)
	{
		printCallCount++;
		log.info("Prepoznata naredba print!");
	}
	//*/
}
