package pp1;

import org.apache.log4j.Logger;

import pp1.ast.SyntaxNode;

public class ErrorLogger {

	public boolean errorDetected = false;

	String errorMsg;
	String infoMsg;

	Logger log = Logger.getLogger(getClass());

	public ErrorLogger(String errorMsg, String infoMsg) {
		this.errorMsg = errorMsg;
		this.infoMsg = infoMsg;
	}

	public ErrorLogger(String errorMsg) {
		this(errorMsg, "Info");
	}

	public String create_message(String message, SyntaxNode node, String color, String msgType) {
		int size = 30 - node.getClass().getSimpleName().length(); // ni jedna klasa nece valjda imati duze ime od 30
		String pad = new String(new char[size]).replace('\0', ' ');

		int size2 = 14 - msgType.length() - String.format("%d", node.getLine()).length();
		String pad2 = new String(new char[size2]).replace('\0', ' ');

		String cls = String.format("%s<%s%s%s>", pad2, Colors.ANSI_RESET, node.getClass().getSimpleName(), color);
		String line = String.format("%s%s (%s%d%s): ", color, msgType, Colors.ANSI_RESET, node.getLine(), color);
		return line + cls + pad + message + Colors.ANSI_RESET;
	}

	public void report_error(String message, SyntaxNode node) {
		errorDetected = true;
		log.error(create_message(message, node, Colors.ANSI_RED, errorMsg));
	}

	public void report_info(String message, SyntaxNode node) {
		log.info(create_message(message, node, Colors.ANSI_GREEN, infoMsg));
	}
}
