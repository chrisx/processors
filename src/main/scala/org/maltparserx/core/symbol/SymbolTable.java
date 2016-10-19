package org.maltparserx.core.symbol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.maltparserx.core.exception.MaltChainedException;
import org.maltparserx.core.symbol.nullvalue.NullValues.NullValueId;

public interface SymbolTable extends Table {
	public int addSymbol(StringBuilder symbol) throws MaltChainedException;
	public Set<Integer> getCodes();
	public void printSymbolTable(Logger logger) throws MaltChainedException;
	public void save(BufferedWriter out) throws MaltChainedException;
	public void load(BufferedReader in) throws MaltChainedException;
	public int getValueCounter();
	public int getNullValueCode(NullValueId nullValueIdentifier) throws MaltChainedException;
	public String getNullValueSymbol(NullValueId nullValueIdentifier) throws MaltChainedException;
	public boolean isNullValue(String value) throws MaltChainedException;
	public boolean isNullValue(int code) throws MaltChainedException;
	public void copy(SymbolTable fromTable) throws MaltChainedException;
}
