package org.dsmk.api;

public interface Log {

	public void debug(String msg, Object... args);

	public void info(String msg, Object... args);

	public void warn(String msg, Object... args);

	public void error(String msg, Object... args);

}
