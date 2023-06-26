package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class MJParserTest {

	static {
		//DOMConfigurator.configure("config/log4j.xml");
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	private static void deleteIfExists(File file) {
		if (file.exists()) {
			file.delete();
		}
	}
	
	private static Logger log = Logger.getLogger(MJParserTest.class);

	public static Program syntaxAnalysis(String filename) throws Exception {
		File sourceCode = new File(filename);
		log.info("Compiling source file: " + sourceCode.getAbsolutePath());

		Reader br = new BufferedReader(new FileReader(sourceCode));
		Yylex lexer = new Yylex(br);

		MJParser p = new MJParser(lexer);
        Symbol s = p.parse();  //pocetak parsiranja
		log.info("===================================");

		if (lexer.errorDetected) {
			throw new Exception("leksicke analize");
		}
		if (p.errorDetected) {
			throw new Exception("sintaksne analize");
		}

        Program prog = (Program)(s.value);
		// ispis sintaksnog stabla
		log.info(prog.toString(""));

		return prog;
	}

	public static SemanticAnalyzer semanticAnalysis(Program prog) throws Exception {
		Tab.init();
		
		SemanticAnalyzer sem = new SemanticAnalyzer();
		prog.traverseBottomUp(sem);

		Tab.dump();

		if (sem.errorDetected()) {
			throw new Exception("semanticke analize");
		}
		return sem;
	}

	public static void CodeGeneration(Program prog, SemanticAnalyzer sem, File objFile) throws FileNotFoundException, Exception {
		CodeGenerator codeGenerator = new CodeGenerator(sem);
		prog.traverseBottomUp(codeGenerator);

		Code.dataSize = sem.nVars;
		Code.mainPc = codeGenerator.getMainPc();

		if (codeGenerator.errorDetected()) {
			throw new Exception("generisanja koda");
		}

		deleteIfExists(objFile);
		Code.write(new FileOutputStream(objFile));
	}

	public static void main(String[] args) throws Exception {
		
		Reader br = null;
		String objFileName = "test/program.obj";
		String sourceFileName = "test/program.mj";

		File objFile = new File(objFileName);
		try {

			Program prog = syntaxAnalysis(sourceFileName);
			log.info("===================================");

			SemanticAnalyzer sem = semanticAnalysis(prog);
			log.info("===================================");

			CodeGeneration(prog, sem, objFile);
			sem.printAllFunctionDecls();
			log.info(String.format("%sGenerisanje koda uspesno zavrseno%s", Colors.ANSI_GREEN, Colors.ANSI_RESET));

		} catch (FileNotFoundException f) {
			log.error(String.format("%s%s%s", Colors.ANSI_RED, f.getMessage(), Colors.ANSI_RESET));
			deleteIfExists(objFile);

		} catch (Exception e) {
			log.error(String.format("%sParsiranje nije uspesno zavrseno, greska se desila tokom %s%s",
					Colors.ANSI_RED, e.getMessage(), Colors.ANSI_RESET));
			deleteIfExists(objFile);

		} finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
