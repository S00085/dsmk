package org.dsmk.api;

import java.util.Map;
import java.util.UUID;

public interface Subsystem {

	public String name();
	
	public UUID id();
	
	public Map<String,Object> attributes();
	
	public Result configure(Map<String,Object> config);
	
	public Result start();
	
	public Result stop();
}
