package org.dsmk.api;

import java.util.Iterator;
import javax.naming.NamingException;

public interface NameServer extends Server {

	public interface NameRegistration{
		
		public String getRegistrationName();
		public Object getRegiseredInstance();
		public boolean isRegistered();
		public void unRegister()throws NamingException;
	}
	
	/**
	 * Lookup a name and get the mapped Object
	 * @param name
	 * @return
	 */
	public Object lookup1(String name) throws NamingException;
	
	/**
	 * Lookup a name and get the list of mapped objects
	 * @param name
	 * @return
	 */
	public Iterator<Object> lookupAll(String name) throws NamingException;
	
	
	/**
	 * Lookup a name and get the mapped object of the specified type
	 * @param name
	 * @param clz
	 * @return
	 */
	public <T> T lookup1(String name,Class<T> typ) throws NamingException;
	
	
	/**
	 * Lookup all the instances mapped to a given name
	 * @param name
	 * @param typ
	 * @return
	 * @throws NamingException
	 */
	public <T> Iterator<T> lookupAll(String name,Class<T> typ) throws NamingException;
	
	
	/**
	 * Registers the given instance to a given name and returns a NameRegistration instance
	 * Note: This instance needs to be maintained to unregister cleanly from the NameServer
	 * @param name
	 * @param instance
	 * @return
	 * @throws NamingException
	 */
	public NameRegistration register(String name,Object instance) throws NamingException;
	
	/**
	 * Checks whether an instance with the given name is registered
	 * @param name
	 * @param instance
	 * @return
	 */
	public boolean isRegistered(String name,Object instance);

}
