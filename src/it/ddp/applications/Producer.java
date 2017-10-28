package it.ddp.applications;

import java.io.File;

import org.apache.log4j.Logger;

public class Producer extends AbstractService {
	
	public Producer(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		log = Logger.getLogger(Producer.class);
		log.debug("Producer init...");
		
		configureWebServer();
		
		// do what needed
		InternalProcessRegistry<Producer> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		startCommunicationServer();
	}
	
	private Logger log = null;
	
	public static final String TYPE = "PRODUCER";
}
