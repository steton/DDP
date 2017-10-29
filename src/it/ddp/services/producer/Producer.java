package it.ddp.services.producer;

import java.io.File;

import org.apache.log4j.Logger;

import it.ddp.services.core.AbstractService;
import it.ddp.services.core.InternalProcessRegistry;

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
	
	@Override
	public ServiceType getType() {
		return ServiceType.PRODUCER;
	}
	
	private Logger log = null;
	
}
