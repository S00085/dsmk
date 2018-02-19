package org.dsmk.kernel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.naming.NamingException;

import org.dsmk.api.Kernel;
import org.dsmk.api.Log;
import org.dsmk.api.LogServer;
import org.dsmk.api.NameServer;
import org.dsmk.api.NameServer.NameRegistration;
import org.dsmk.api.Result;
import org.dsmk.api.Result.Status;
import org.dsmk.api.Subsystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.krukow.clj_ds.PersistentMap;
import com.github.krukow.clj_lang.PersistentHashMap;
import com.github.krukow.clj_lang.PersistentList;
import static com.google.common.base.Preconditions.*;

public class Main implements Kernel {

	private static String kernelResourceBundleName = "dsmkmessages";
	
	private static UUID kernelUUID = UUID.fromString("f9af9c66-14f4-4935-b70e-944c9540ffbc");
	private static String NAME = "dsmk.kernel";
	private static final Map<String, Object> persistentMap;
	static {
		Map<String, Object> tempMap = new HashMap();
		tempMap.put("UUID", kernelUUID);
		tempMap.put("NAME", NAME);
		persistentMap = PersistentHashMap.create(tempMap);
	}

	private static final class MsgCodes {
		static final String ERR_KERNEL_FAILED_ON_INIT = "dsmk.kernel.err.init.0001";
		static final String ERR_KERNEL_FAILED_ON_LOG_SERVER_INIT = "dsmk.kernel.err.logserver.init.0001";
		static final String ERR_KERNEL_FAILED_ON_MESSAGES_INIT = "dsmk.kernel.err.messages.init.0001";
		static final String ERR_KERNEL_FAILED_ON_CONF_INIT = "dsmk.kernel.err.conf.0001";
		static final String ERR_KERNEL_NOT_OK = "dsmk.kernel.err.notok";
		static final String ERR_KERNEL_OK = "dsmk.kernel.err.ok";
		static final String MSG_KERNEL_STOP_OK = "dsmk.kernel.msg.stop.ok";
		static final String MSG_KERNEL_SUCCESSFUL = "dsmk.kernel.msg.successful";
		public static final String WARN_KERNEL_NOT_OK = "dsmk.kernel.warn.notok";
		public static final String WARN_KERNEL_NO_SUBSYS_CONFIGURED = "dsmk.kernel.warn.subsys.none";
		public static final String ERR_KERNEL_FAILED_ON_NAMING_SERVER_INIT = "dsmk.kernel.err.nameserver.init.0001";
		public static final String ERR_KERNEL_FAILED_ON_LOG_SERVER_REGISTRATION = "dsmk.kernel.err.logserver.registration.0001";
		public static final String ERR_KERNEL_FAILED_ON_SUBSYS_INSTANTIATION = "dsmk.kernel.err.subsys.instantiation.0001";
		public static final String ERR_KERNEL_FAILED_ON_FINAL_SUBSYS_CONFIG = null;
		public static final String ERR_KERNEL_FAILED_ON_FINAL_SUBSYS_CLASS_NOT_LOADED = null;
		public static final String ERR_KERNEL_FAILED_ON_FINAL_SUBSYS_FAILED_TO_INSTANTIATE = null;

	};

	private LogServer logServer;
	private NameServer nameServer;

	private NameRegistration logServerRegistration;

	private Log kernelLog;

	private ResourceBundle messages;

	private Properties configuration;

	private List<Subsystem> preSubsystems;
	private List<Subsystem> extSubsystems;
	private List<Subsystem> postSubsystems;

	private Thread shutdownHook = new Thread() {

		@Override
		public void run() {
			try {
				Result result = Main.this.stop();
				if (result.isNotOK()) {
					System.err.println(message("dsmk.kernel.stop.err", result.getCode()));
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}

	};

	private String[] arguments;

	private NameRegistration configPropertiesRegistration;

	private NameRegistration argumentsRegistration;

	public String name() {
		return NAME;
	}

	protected String message(String string, String code) {
		// TODO Auto-generated method stub
		return null;
	}

	public UUID id() {
		return kernelUUID;
	}

	public Map<String, Object> attributes() {
		return persistentMap;
	}

	/**
	 * The entry point for the kernel. Everything starts here
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		/**
		 * Initializes the logging subsystem first before doing anything. If the logging
		 * subsystem succeeds then looks up the other subsystems configuration in
		 * META-INF/dsmk.kernel.properties and starts the subsystems defined in order
		 * else if the logging subsystem fails then prints the error to the system.err
		 * with the reason and does a hard exit end if
		 */
		Map<String, Object> kernelConfig;
		try {
			kernelConfig = createKernelConfiguration(args);
			Main kernel = new Main();
			kernel.configure(kernelConfig);
			/**
			 * Add a shutdown hook to the JVM so we can listen for shutdown events and
			 * trigger kernel & system clean up
			 */

			Runtime.getRuntime().addShutdownHook(kernel.shutdownHook);

			kernel.start();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IOException e) {
			e.printStackTrace(System.err);
			System.err.println("Panic : Kernel startup failed...");
		}

	}

	private static Map<String, Object> createKernelConfiguration(String[] args)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {

		Properties props = getConfigProperties();
		Map<String, Object> config = new HashMap<>();
		config.put("pre-subsystems", getPreSubsystems(props));
		config.put("ext-subsystems", getExtSubsystems(props));
		config.put("post-subsystems", getPostSubsystems(props));
		config.put("config-properties", props);
		config.put("startup-arguments", args);
		return config;
	}

	private static Properties getConfigProperties() throws IOException {
		Properties systemProperties = System.getProperties();
		Properties finalConfigProperties = new Properties();
		InputStream inStream = Main.class.getResourceAsStream("/dsmk-conf.properties");
		if(inStream != null) {
			finalConfigProperties.load(inStream);
		}

		// merge both the properties. the System properties passed from outside
		// overrides the one loaded from the file
		finalConfigProperties.putAll(systemProperties);

		return finalConfigProperties;
	}

	private static List<Subsystem> getPostSubsystems(Properties props)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		/*
		 * ArrayList<Subsystem> list = new ArrayList(); list.add(new FinalSubsystem());
		 * return list;
		 */

		return loadSubsystem("dsmk.post.subsystem", props);
	}

	private static List<Subsystem> getExtSubsystems(Properties props)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		/*
		 * String extSubsystemString = System.getProperty("dsmk.ext.subsystem");
		 * ArrayList<Subsystem> extSubsystems = new ArrayList<>(); if
		 * (extSubsystemString != null) { String[] splitExtSubsystems =
		 * extSubsystemString.split(","); if (splitExtSubsystems.length > 0) { for
		 * (String subsys : splitExtSubsystems) { Class subsysClz =
		 * Main.class.getClassLoader().loadClass(subsys); Subsystem subsysInst =
		 * (Subsystem) subsysClz.newInstance(); extSubsystems.add(subsysInst); } }
		 * 
		 * } else { System.out.println("No extension subsystems configured..."); }
		 * 
		 * return extSubsystems;
		 */

		return loadSubsystem("dsmk.ext.subsystem", props);
	}

	private static List<Subsystem> getPreSubsystems(Properties props)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		return loadSubsystem("dsmk.pre.subsystem", props);
	}

	/**
	 * This method does the following For a given group name looks for
	 * <groupname>.count property if the property is found gets the value and
	 * converts it to integer then loops for count looking for <groupname>.<number>
	 * property and loads the class and instantiates it if loading and instantiation
	 * succeeds then adds to the subsystem list once the count is exhausted it
	 * returns the subsystem list If any of the below happens errors are thrown if
	 * the property is not found if the property for instance is not found inbetween
	 * are ignored by printing a warning else if the property is not found returns
	 * an error "groupname.count" is undefined
	 * 
	 * @param groupname
	 * @param props
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	private static List<Subsystem> loadSubsystem(String groupname, Properties props)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		String countProperty = String.format("%s.count", groupname);
		String classPropertyPattern = "%s.%d.class";
		int count = Integer.parseInt(props.getProperty(countProperty, "0"));
		ArrayList<Subsystem> subsystemList = new ArrayList();
		for (int i = 0; i < count; i++) {
			String clzProperty = String.format(classPropertyPattern, groupname, i);
			String clzName = props.getProperty(clzProperty);
			if(clzName != null) {
				Class clz = Main.class.getClassLoader().loadClass(clzName);
				Subsystem subsys = (Subsystem) clz.newInstance();
				subsystemList.add(subsys);
			}else {
				System.err.println(String.format("WARN: Unable to find entry for property %s, however %s=%d", clzProperty,countProperty,count));
			}
		}
		return subsystemList;
	}

	public Result start() {

		/**
		 * The kernel does the following on start. in all the below cases if there are
		 * errors the kernel errors are returned from this method 1.Start the logServer
		 * 2.Load the kernel messages from dsmkmessages_en.properties 3.Start the
		 * NameServer 4.Load the kernel configuration from dsmkconf.properties 4.1 -
		 * Registers the logServer in the NameServer so that the subsystems which are
		 * yet to be started can access them through the NameServer 5.Logs the subsystem
		 * startup order picked up from the kernel configuration mentioned 6.Instantiate
		 * the configured subsystems. If any errors during instantiation kernel is
		 * halted 7.Initialize the subsystems by calling configure(map) with the
		 * nameserver as one of the inputs. This is required so that subsystems can get
		 * hold of the nameserver so that they can access other servers from the
		 * nameservers. 8.Check if there is a user defined subsystem configured as part
		 * of the dsmk.ext.subsystem system property 9.If there is one defined, then try
		 * to load and instantiate it. 10. If the user defined subsystem is found and
		 * instantiated, then configure and start it. If any of the above steps fail
		 * print the error and exit from kernel 11. At the end find and start the
		 * final.subsystem configured in dsmkconf.properties. 12. If all goes well the
		 * kernel is assumed to be successfully started 13. Finally register all the
		 * above mentioned subsystems in the Nameserver
		 */
		try {
			kernelLog = logServer.getLog(this);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK, MsgCodes.ERR_KERNEL_NOT_OK, MsgCodes.ERR_KERNEL_FAILED_ON_LOG_SERVER_INIT);
		}

		try {
			startNamingServer();

		} catch (Exception e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK, MsgCodes.ERR_KERNEL_NOT_OK,
					MsgCodes.ERR_KERNEL_FAILED_ON_NAMING_SERVER_INIT);
		}

		Result registrationResult = registerNames();

		if (registrationResult.isNotOK()) {
			kernelLog.debug("Kernel startup failed!...");
			return registrationResult;
		}

		logSubsystemsStartupOrder();

		PersistentMap<String, Object> initMap = createInitMap();

		kernelLog.debug("About to initialize and start pre-subsystems");

		Result results = initializeAndStartSubsystems(preSubsystems, initMap);

		if (results.isNotOK()) {
			kernelLog.debug("kernel startup failed!...");
			return results;
		}

		kernelLog.debug("About to initialize and start ext-subsystems");

		results = initializeAndStartSubsystems(extSubsystems, initMap);

		if (results.isNotOK()) {
			kernelLog.debug("kernel startup failed!...");
			return results;
		}

		kernelLog.debug("About to initialize and start post-subsystems");

		results = initializeAndStartSubsystems(postSubsystems, initMap);

		if (results.isNotOK()) {
			kernelLog.debug("kernel startup failed!...");
			return results;
		}

		kernelLog.debug("kernel started successfully!...");
		return new Result(Status.OK, MsgCodes.ERR_KERNEL_OK, MsgCodes.MSG_KERNEL_SUCCESSFUL);

	}

	private Result initializeAndStartSubsystems(List<Subsystem> subsystems, PersistentMap<String, Object> initMap) {

		Result initResults = initializeSubsystems(subsystems, initMap);

		if (initResults.isNotOK()) {
			kernelLog.debug("kernel startup failed!...");
			return initResults;
		}

		Result startResults = startSubsystems(subsystems);

		if (startResults.isNotOK()) {
			kernelLog.debug("kernel startup failed!...");
		}

		return startResults;
	}

	private Result registerNames() {

		kernelLog.debug("Registering Log server {} to NameServer..", logServer);

		try {
			logServerRegistration = nameServer.register(LogServer.class.getName(), logServer);

			kernelLog.debug("Log Server Registration completed : NameRegistration {}", logServerRegistration);
			
			configPropertiesRegistration = nameServer.register("dsmk-conf.properties", configuration);
			
			kernelLog.debug("Kernel configuration Registration completed : NameRegistration {}", configPropertiesRegistration);
			
			argumentsRegistration = nameServer.register("startup-arguments", arguments);
			
			kernelLog.debug("Arguments Registration completed : NameRegistration {}", argumentsRegistration);

		} catch (NamingException e) {

			kernelLog.debug("Log Server Registration failed with exception message {}", e.getMessage());
			e.printStackTrace(System.err);

			return new Result(Status.NOT_OK, MsgCodes.ERR_KERNEL_NOT_OK,
					MsgCodes.ERR_KERNEL_FAILED_ON_LOG_SERVER_REGISTRATION);
		}

		return Result.OK;
	}

	private void startNamingServer() {
		kernelLog.debug("Starting Name Server...");

		nameServer = new DefaultNameServer();

		kernelLog.debug("Name server started...");
	}

	private void logSubsystemsStartupOrder() {
		kernelLog.debug("Pre-Subsystems to be started in order... ");
		logSubsystemStartupOrder(preSubsystems);

		kernelLog.debug("Ext-Subsystems to be started in order... ");
		logSubsystemStartupOrder(extSubsystems);

		kernelLog.debug("Post-Subsystems to be started in order... ");
		logSubsystemStartupOrder(postSubsystems);

	}

	private void logSubsystemStartupOrder(List<Subsystem> subsystems) {
		subsystems.forEach(subsystem -> {
			kernelLog.debug("{}-{}", subsystem.name(), subsystem.id());
		});
	}

	private Result startSubsystems(List<Subsystem> subsystems) {

		for (Subsystem subsys : subsystems) {
			Result result = subsys.start();
			if (result.isNotOK()) {
				kernelLog.debug("Start failed for Subsystem {}-{}", subsys.name(), subsys.id());
				return result;
			}
		}

		return Result.OK;
	}

	private Result initializeSubsystems(List<Subsystem> subsystems, PersistentMap<String, Object> initMap) {

		// PersistentMap<String, Object> initMap = createInitMap();

		for (Subsystem subsys : subsystems) {
			Result result = subsys.configure(initMap);
			if (result.isNotOK()) {
				kernelLog.debug("Configuration failed for Subsystem {}-{}", subsys.name(), subsys.id());
				return result;
			}
		}

		return Result.OK;
	}

	private PersistentMap<String, Object> createInitMap() {
		Map serverMap = new HashMap();
		serverMap.put(NameServer.class.getName(), this.nameServer);
		//send the complete configuration properties which the kernel has 
		serverMap.put("config-properties",configuration);

		return PersistentHashMap.create(serverMap);

	}

	private void loadMessages() {
		kernelLog.debug("loading {} for locale {}", kernelResourceBundleName, Locale.getDefault().ENGLISH);

		messages = ResourceBundle.getBundle(kernelResourceBundleName, Locale.getDefault().ENGLISH);

		kernelLog.debug("loading {} for locale {} completed!", kernelResourceBundleName, Locale.getDefault().ENGLISH);
	}

	public Result stop() {

		try {

			kernelLog.debug("About to stop post-subsystems in reverse order...");
			Result stopResult = stopSubsystems(postSubsystems);

			if (stopResult.isNotOK()) {
				kernelLog.error("Stopping post-subsystems failed with result {}", stopResult);
			}

			kernelLog.debug("About to stop ext-subsystems in reverse order...");
			stopResult = stopSubsystems(extSubsystems);

			if (stopResult.isNotOK()) {
				kernelLog.error("Stopping ext-subsystems failed with result {}", stopResult);
			}

			kernelLog.debug("About to stop pre-subsystems in reverse order...");
			stopResult = stopSubsystems(preSubsystems);

			if (stopResult.isNotOK()) {
				kernelLog.error("Stopping pre-subsystems failed with result {}", stopResult);
			}

		} catch (Exception e) {
			e.printStackTrace(System.err);
			kernelLog.error("Stopping subsystems failed with error {}", e.getMessage());
		}

		Result unregisterResult = unregisterNames();

		if (unregisterResult.isNotOK()) {
			kernelLog.info("Failures during unregistering for kernel servers...");
		} else {
			kernelLog.info("Unregistered Kernel servers.Halting system.Good bye!");
		}

		return new Result(Status.OK, MsgCodes.MSG_KERNEL_STOP_OK, MsgCodes.MSG_KERNEL_SUCCESSFUL);
	}

	private Result stopSubsystems(List<Subsystem> subsystemObjList2) {
		kernelLog.info("Stopping subsystems in reverse order...");

		Result result = null;

		for (int i = subsystemObjList2.size() - 1; i > -1; i--) {
			Subsystem subsys = subsystemObjList2.get(i);
			try {
				result = subsys.stop();
			} catch (Exception e) {
				e.printStackTrace(System.err);
				kernelLog.info("Failed to stop subsystem {}-{}", subsys.name(), subsys.id());
			}
		}

		kernelLog.info("Stopped subsystems in reverse order..");
		return Result.OK;
	}

	private Result unregisterNames() {

		kernelLog.debug("Unregistering kernel registered names from NameServer...");

		try {
			logServerRegistration.unRegister();
			kernelLog.debug("Unregistered log server..");

			configPropertiesRegistration.unRegister();
			kernelLog.debug("Unregistered config properties..");
			argumentsRegistration.unRegister();
			kernelLog.debug("Unregistered arguments..");
		} catch (NamingException e) {
			kernelLog.debug("Unregistered kernel registered names failed with error - {}", e.getMessage());
			e.printStackTrace(System.err);
			return Result.NOT_OK;
		}
		return Result.OK;
	}

	@Override
	public Result configure(Map<String, Object> config) {
		try {
			preSubsystems = (List<Subsystem>) config.getOrDefault("pre-subsystems", new ArrayList<Subsystem>());
			extSubsystems = (List<Subsystem>) config.getOrDefault("ext-subsystems", new ArrayList<Subsystem>());
			postSubsystems = (List<Subsystem>) config.getOrDefault("post-subsystems", new ArrayList<Subsystem>());
			logServer = (LogServer) config.getOrDefault("log-server", new DefaultLogServer());
			messages = (ResourceBundle) config.getOrDefault("messages",
					ResourceBundle.getBundle("dsmkmessages", Locale.ENGLISH));
			configuration = (Properties) config.getOrDefault("config-properties", Main.getConfigProperties());
			arguments = (String[])config.getOrDefault("startup-arguments", new String[] {});
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK, "kernel.configure.notok", "kernel.configure.err.00001");
		}
		return Result.OK;
	}

	private class DefaultLog implements Log {

		private Logger logger;

		DefaultLog(Logger logger) {
			this.logger = logger;
		}

		public void debug(String msg, Object... args) {
			if (logger.isDebugEnabled()) {
				logger.debug(msg, args);
			}

		}

		public void info(String msg, Object... args) {
			if (logger.isInfoEnabled()) {
				logger.info(msg, args);
			}
		}

		public void warn(String msg, Object... args) {
			if (logger.isWarnEnabled()) {
				logger.warn(msg, args);
			}
		}

		public void error(String msg, Object... args) {
			if (logger.isErrorEnabled()) {
				logger.error(msg, args);
			}

		}

	}

	private class DefaultLogServer implements LogServer {

		public Log getLog(Subsystem sys) {

			return new DefaultLog(LoggerFactory.getLogger(sys.name()));

		}

		@Override
		public String toString() {
			return "Main.DefaultLogServer";
		}

	}

	private class DefaultNameServer implements NameServer {

		private ConcurrentHashMap<String, ConcurrentLinkedQueue<Object>> registry = new ConcurrentHashMap<>();

		private class DefaultNameRegistration implements NameRegistration {

			private String name;
			private Object instance;
			private DefaultNameServer server;

			DefaultNameRegistration(String name, Object instance, DefaultNameServer srv) {
				this.name = name;
				this.instance = instance;
				this.server = srv;
			}

			@Override
			public String getRegistrationName() {
				return name;
			}

			@Override
			public Object getRegiseredInstance() {
				return instance;
			}

			@Override
			public boolean isRegistered() {
				return server.isRegistered(name, instance);
			}

			@Override
			public void unRegister() throws NamingException {
				server.unRegister(name, instance);
			}

			@Override
			public String toString() {
				return "DefaultNameRegistration [name=" + name + ", instance=" + instance + "]";
			}

		}

		@Override
		public Object lookup1(String name) throws NamingException {

			return lookupAll(name).next(); // get the first one in the list
		}

		@Override
		public Iterator<Object> lookupAll(String name) throws NamingException {
			try {
				checkArgument(name != null, "Lookup name cannot be null!");
				checkArgument(!name.trim().isEmpty(), "Lookup name cannot be empty!");

				if (!registry.containsKey(name)) {
					throw new NamingException(String.format("Name {} not found", name));
				}

				Queue nameList = (Queue) registry.get(name);

				if (nameList.isEmpty()) {
					throw new NamingException(String.format("No registration found for Name {} ", name));
				}

				return nameList.iterator();
			} catch (Exception e) {
				throw new NamingException(e.getMessage());
			}
		}

		@Override
		public <T> T lookup1(String name, Class<T> typ) throws NamingException {
			try {
				checkArgument(typ != null, "Lookup Type cannot be null!");
				Object inst = lookup1(name);
				if (typ.isAssignableFrom(inst.getClass())) {
					return (T) inst;
				} else {
					throw new NamingException(String.format("Looked up instance %s-%s is not of type expected %s", inst,
							inst.getClass(), typ));
				}

			} catch (Exception e) {
				throw new NamingException(e.getMessage());
			}
		}

		@Override
		public <T> Iterator<T> lookupAll(String name, Class<T> typ) throws NamingException {
			try {
				checkArgument(typ != null, "Lookup Type cannot be null!");
			} catch (Exception e) {
				throw new NamingException(e.getMessage());
			}

			Iterator<Object> instances = lookupAll(name);

			com.github.krukow.clj_ds.PersistentList<T> finalList = null;

			while (instances.hasNext()) {
				Object inst = instances.next();
				if (!typ.isAssignableFrom(inst.getClass())) {
					throw new NamingException(String.format(
							"One of Looked up instances %s-%s is not of type expected %s", inst, inst.getClass(), typ));
				}

				if (finalList == null) {
					finalList = (PersistentList<T>) PersistentList.create((T) inst);
				} else {
					finalList = finalList.plus((T) inst);
				}

			}

			return finalList.iterator();

		}

		@Override
		public NameRegistration register(String name, Object instance) throws NamingException {
			try {
				checkArgument(name != null, "Registered name cannot be null!");
				checkArgument(!name.trim().isEmpty(), "Registered name cannot be empty!");
				checkArgument(instance != null, "Registered instance cannot be null!");

				ConcurrentLinkedQueue<Object> instList = null;

				if (registry.containsKey(name)) {
					instList = (ConcurrentLinkedQueue) registry.get(name);
				} else {
					instList = new ConcurrentLinkedQueue();
				}

				if (instList != null) {
					instList.add(instance);
				}

				// add this back to the registry
				registry.put(name, instList);

				NameRegistration registration = new DefaultNameRegistration(name, instance, this);

				return registration;
			} catch (Exception e) {
				throw new NamingException(e.getMessage());
			}
		}

		public void unRegister(String name, Object instance) throws NamingException {
			try {
				checkArgument(name != null, "Name to be unregistered cannot be null!");
				checkArgument(!name.trim().isEmpty(), "Name to be unregistered cannot be empty!");
				checkArgument(instance != null, "Instance to be unregistered cannot be null!");

				ConcurrentLinkedQueue instList = null;

				if (registry.containsKey(name)) {
					instList = (ConcurrentLinkedQueue) registry.get(name);
				} else {
					throw new NamingException(String.format("Name % not found", name));
				}

				if (instList != null) {
					if (instList.contains(instance)) {
						instList.remove(instance);
					} else {
						throw new NamingException(String.format(
								"Instance %s for Name % does not seem to be registered. \n Please check the name or instance it is registered under",
								name));
					}
				} else {
					throw new NamingException(String.format("No instance registered with Name %s", name));
				}

				// add this back to the registry
				registry.put(name, instList);

			} catch (Exception e) {
				throw new NamingException(e.getMessage());
			}
		}

		@Override
		public boolean isRegistered(String name, Object instance) {
			boolean retVal = false;
			try {
				Iterator<Object> instances = lookupAll(name);
				while(instances.hasNext()) {
					Object inst = instances.next();
					if(inst == instance) {
						retVal = true;
					}
				}
			} catch (NamingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return retVal;
		}
	}

}
