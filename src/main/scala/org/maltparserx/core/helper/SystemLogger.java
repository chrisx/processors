package org.maltparserx.core.helper;

import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.core.layout.PatternLayout;

/**
*
*
* @author Johan Hall
*/
public class SystemLogger {
	private static SystemLogger uniqueInstance = new SystemLogger();
	private static Logger systemLogger;
	private Level systemVerbosityLevel;
	//private ConsoleAppender consoleAppender;
	
	private SystemLogger() { 
		systemLogger = LogManager.getLogger("System");
		/* System verbosity (Standard Out) */
		//consoleAppender = new ConsoleAppender(new PatternLayout("%m"), ConsoleAppender.SYSTEM_ERR);
		
		//PatternLayout pb = PatternLayout.createDefaultLayout();
//		PatternLayout pb = PatternLayout.newBuilder().withPattern("%m").build();
//		consoleAppender = ConsoleAppender.createAppender(pb, null, "System.err", "console", null, null);
				
//				new ConsoleAppender(pb, "System.err");
//		consoleAppender.setEncoding("UTF-16");
		
		
		//systemLogger.addAppender(consoleAppender, null, null);
		if (System.getProperty("Malt.verbosity") != null) {
			setSystemVerbosityLevel(System.getProperty("Malt.verbosity").toUpperCase());
		} else {
			setSystemVerbosityLevel("INFO");
		}
	}
	
	/**
	* Returns a reference to the single instance.
	*/
	public static SystemLogger instance() {
		return uniqueInstance;
	}
	
	/**
	* Returns a reference to the Logger.
	*/
	public static Logger logger() {
		return systemLogger;
	}
	
	/**
	 * Returns the system verbosity level
	 * 
	 * @return the system verbosity level
	 */
	public Level getSystemVerbosityLevel() {
		return systemVerbosityLevel;
	}

	/**
	 * Sets the system verbosity level
	 * 
	 * @param verbosity	a system verbosity level
	 */
	public void setSystemVerbosityLevel(String verbosity) {
		this.systemVerbosityLevel = Level.toLevel(verbosity, Level.INFO);
		//consoleAppender.setThreshold(systemVerbosityLevel);
		//systemLogger.setLevel(systemVerbosityLevel);
	}
}
