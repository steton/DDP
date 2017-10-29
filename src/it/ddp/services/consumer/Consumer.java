package it.ddp.services.consumer;

import java.io.File;

import org.apache.log4j.Logger;

import it.ddp.services.core.AbstractService;
import it.ddp.services.core.InternalProcessRegistry;

public class Consumer extends AbstractService {
	
	public Consumer(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		
		log = Logger.getLogger(Consumer.class);
		log.debug("Consumer init...");
		
		configureWebServer();
		
		// do what needed
		InternalProcessRegistry<Consumer> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		startCommunicationServer();
	}
	
	private Logger log = null;
	
	public static final String TYPE = "CONSUMER";
}
