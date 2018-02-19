package org.dsmk.kernel.subsys.event;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

import javax.naming.NamingException;

import org.dsmk.api.Log;
import org.dsmk.api.LogServer;
import org.dsmk.api.NameServer;
import org.dsmk.api.NameServer.NameRegistration;
import org.dsmk.api.Result;
import org.dsmk.api.Result.Status;
import org.dsmk.api.Subsystem;

import com.github.krukow.clj_lang.PersistentHashMap;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

public class EventSubsystem implements Subsystem {

	private static UUID uuid = UUID.fromString("e18e7284-520e-4e68-987a-2d1107acda40");
	private static String NAME = "dsmk.subsys.event";
	
	private static final Map<String, Object> persistentMap;
	static {
		Map<String, Object> tempMap = new HashMap();
		tempMap.put("UUID", uuid);
		tempMap.put("NAME", NAME);
		persistentMap = PersistentHashMap.create(tempMap);
	}
	
	private NameServer nameServer;
	
	private NameRegistration eventBusRegistration;
	
	private Log log;

	private ResourceBundle messages;
	
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
	public Result start() {
		
		log.info("Starting EventSubsystem - {}", name());
		Result result = startEventBus();
		
		log.info("Started EventSubsystem - {}", name());
		return result;
	}

	private Result startEventBus() {
		
		log.debug("Starting eventbus...");
		
		EventBus eventBus = new EventBus("eventbus");
		
		try {
			eventBusRegistration = nameServer.register(EventBus.class.getName(), eventBus);
		} catch (NamingException e) {
			e.printStackTrace(System.err);
			log.error("Failed to register eventbus due to {}",e.getMessage() );
			return new Result(Status.NOT_OK,"dsmk.event.eventbus.notok","dsmk.event.eventbus.start.err");
		}
		
		log.debug("Started & Registered eventbus...");
		return	Result.OK;
	}

	@Override
	public Result stop() {
		log.debug("Stopping eventbus..");
		
		log.debug("Unregistering eventbus with registration {}",eventBusRegistration);
		
		
		try {
			eventBusRegistration.unRegister();
		} catch (NamingException e) {
			e.printStackTrace(System.err);
			log.error("Failed to unregister eventbus due to {}",e.getMessage() );
			return new Result(Status.NOT_OK,"dsmk.event.eventbus.notok","dsmk.event.eventbus.stop.err");
		}
		
		return Result.OK;
	}

	@Override
	public Result configure(Map<String, Object> config) {
		nameServer = (NameServer)config.get(NameServer.class.getName());
		
		Preconditions.checkArgument(nameServer != null, "A valid instance of %s is required for configuring EventSubsystem %s",NameServer.class.getName(),name());
		
		try {
			LogServer logServer = nameServer.lookup1(LogServer.class.getName(),LogServer.class);
			log = logServer.getLog(this);
		} catch (NamingException e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK,"dmsk.event.notok","dmsk.event.err.onConfig");
		}
		
		//log = logServer.getLog(this);
		
		log.debug("{} configuration complete", name());
		return Result.OK;
	}

}
