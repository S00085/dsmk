package org.dsmk.kernel.subsys.lastone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.naming.NamingException;

import org.dsmk.api.Log;
import org.dsmk.api.LogServer;
import org.dsmk.api.NameServer;
import org.dsmk.api.Result;
import org.dsmk.api.Subsystem;
import org.dsmk.api.Result.Status;

import com.github.krukow.clj_lang.PersistentHashMap;
import com.google.common.base.Preconditions;

public class FinalSubsystem implements Subsystem {

	
	private static UUID uuid = UUID.fromString("46c7d8ad-186c-4942-8f46-9754dcf0f11a");
	private static String NAME = "dsmk.subsys.finalone";
	
	private static final Map<String, Object> persistentMap;
	static {
		Map<String, Object> tempMap = new HashMap();
		tempMap.put("UUID", uuid);
		tempMap.put("NAME", NAME);
		persistentMap = PersistentHashMap.create(tempMap);
	}
	
	private NameServer nameServer;
	
	private Log log;
	
	
	@Override
	public String name() {
		return NAME;
	}

	@Override
	public UUID id() {
		return uuid;
	}

	@Override
	public Map<String, Object> attributes() {
		return persistentMap;
	}

	@Override
	public Result configure(Map<String, Object> config) {
		nameServer = (NameServer)config.get(NameServer.class.getName());
		
		Preconditions.checkArgument(nameServer != null, "A valid instance of %s is required for configuring FinalSubsystem %s",NameServer.class.getName(),name());
		
		try {
			LogServer logServer = nameServer.lookup1(LogServer.class.getName(),LogServer.class);
			log = logServer.getLog(this);
		} catch (NamingException e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK,"dmsk.finalsubsys.notok","dmsk.finalsubsys.err.onConfig");
		}
		
		//log = logServer.getLog(this);
		
		log.debug("{} configuration complete", name());
		return Result.OK;
	}

	@Override
	public Result start() {
		log.info("Starting FinalSubsystem - {}", name());
		
		log.info("Started FinalSubsystem - {}", name());
		
		return Result.OK;
	}

	@Override
	public Result stop() {
		log.info("Stopping FinalSubsystem - {}", name());
		
		log.info("Stopped FinalSubsystem - {}", name());
		
		return Result.OK;
	}

}
