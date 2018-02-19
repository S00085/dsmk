package org.dsmk.api;

public interface LogServer extends Server {

	public Log getLog(Subsystem sys);
	
}
