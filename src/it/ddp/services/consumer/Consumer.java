package it.ddp.services.consumer;

import java.io.File;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;

import it.ddp.services.core.AbstractService;
import it.ddp.services.core.InternalProcessRegistry;

public class Consumer extends AbstractService {
	
	public Consumer(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		
		log = Logger.getLogger(Consumer.class);
		log.debug("Consumer init...");
		
		configureWebServer();
		configureConsumer();
		
		// do what needed
		InternalProcessRegistry<Consumer> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		
		startInternalServices();
		startCommunicationServer();
	}
	
	@Override
	public ServiceType getServiceType() {
		return ServiceType.CONSUMER;
	}
	
	@Override
	public String getServiceName() {
		return consumerName;
	}
	
	@Override
	public String getServiceClusterManagerHost() {
		return consumerClusterManagerHost;
	}

	@Override
	public Integer getServiceClusterManagerPort() {
		return consumerClusterManagerPort;
	}
	
	@Override
	protected void executeServiceStrategy() {
		// TODO Auto-generated method stub
		
	}
	
	private void configureConsumer() throws ConfigurationException {
		XMLConfiguration conf = getConfig();	
		consumerName = conf.getString("consumer[@name]", NONE_STRING);
		consumerClusterManagerHost = conf.getString("consumer[@cmhost]", NONE_STRING);
		consumerClusterManagerPort = conf.getInteger("consumer[@cmport]", null);
	}
	
	private Logger log = null;
	
	private String consumerName = null;
	private String consumerClusterManagerHost = null;
	private Integer consumerClusterManagerPort = null;
	
	
}
