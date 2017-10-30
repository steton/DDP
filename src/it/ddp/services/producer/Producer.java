package it.ddp.services.producer;

import java.io.File;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;

import it.ddp.services.core.AbstractService;
import it.ddp.services.core.InternalProcessRegistry;

public class Producer extends AbstractService {
	
	public Producer(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		log = Logger.getLogger(Producer.class);
		log.debug("Producer init...");
		
		configureWebServer();
		configureProducer();
		
		// do what needed
		InternalProcessRegistry<Producer> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		startCommunicationServer();
	}
	
	@Override
	public ServiceType getServiceType() {
		return ServiceType.PRODUCER;
	}
	
	@Override
	public String getServiceName() {
		return producerName;
	}
	
	@Override
	public String getServiceClusterManagerHost() {
		return producerClusterManagerHost;
	}

	@Override
	public Integer getServiceClusterManagerPort() {
		return producerClusterManagerPort;
	}
	
	private void configureProducer() throws ConfigurationException {
		XMLConfiguration conf = getConfig();	
		producerName = conf.getString("producer[@name]", NONE_STRING);
		producerClusterManagerHost = conf.getString("producer[@cmhost]", NONE_STRING);
		producerClusterManagerPort = conf.getInteger("producer[@cmport]", null);
	}
	
	private Logger log = null;
	
	private String producerName = null;
	private String producerClusterManagerHost = null;
	private Integer producerClusterManagerPort = null;
	
}
