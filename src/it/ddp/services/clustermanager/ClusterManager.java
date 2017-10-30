package it.ddp.services.clustermanager;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.log4j.Logger;

import it.ddp.common.objects.ServiceDescriptor;
import it.ddp.common.remote.RemoteConnector;
import it.ddp.services.core.AbstractService;
import it.ddp.services.core.FunctionResult;
import it.ddp.services.core.InternalProcessRegistry;


public class ClusterManager extends AbstractService {

	public ClusterManager(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		log = Logger.getLogger(ClusterManager.class);
		
		configureWebServer();
		configureClusterManager();
		
		log.debug("Activate cluster manager.");
		
		for(ClusterManagerServiceCheck sac : serviceAgentList) {
			sac.enable(true);
		}
		
		InternalProcessRegistry<ClusterManager> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		
		startCommunicationServer();
	}
	
	
	public Map<String, ServiceDescriptor> getServiceLists() {
		return remoteServicesMap;
	}
	
	
	public FunctionResult registerService(ServiceDescriptor sd) {
		FunctionResult res = FunctionResult.UNKNOWN;
		
		if(remoteServicesMap.containsKey(sd.name)) {
			res = FunctionResult.DATA_UPDATE;
		}
		else {
			res = FunctionResult.OK;
		}
		
		remoteServicesMap.put(sd.name, sd);
		
		return res;
	}
	
	
	public void close() throws Exception {
		log.debug(String.format("Stopping ClusterManager '%s'.", clusterName));
		
		for(ClusterManagerServiceCheck sac : serviceAgentList) {
			sac.enable(false);
		}
		
		/*
		internalScheduler.shutdown();
		try {
			log.debug("Waiting for agentScheduler termination");
			//internalScheduler.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch(InterruptedException e) {
			log.error("Timeout occurred.", e);
		}
		*/
		
		log.debug(String.format("Plugin '%s' closed.", clusterName));
	}
	
	@Override
	public ServiceType getServiceType() {
		return ServiceType.CLUSTERMANAGER;
	}
	
	@Override
	public String getServiceName() {
		return clusterName;
	}
	
	@Override
	public String getServiceClusterManagerHost() {
		return null;
	}

	@Override
	public Integer getServiceClusterManagerPort() {
		return null;
	}
	
	// ------------------------------------------------------------------
	// -- P R I V A T E   M E T H O D S ---------------------------------
	// ------------------------------------------------------------------	
	
	private void configureClusterManager() throws Exception {
		XMLConfiguration conf = getConfig();
		
		remoteServicesMap = new HashMap<>();
		serviceAgentList = new ArrayList<>();
		
		clusterName = conf.getString("clustermanager[@name]", NONE_STRING);
		serviceAgentPollingPolicy = conf.getString("clustermanager.serviceagents.checks.pollingpolicy[@value]", "");
		serviceAgentPollingTimeout = conf.getLong("clustermanager.serviceagents.checks.pollingtimeout[@value]", -1L);
		serviceAgentMaxPollingRetries = conf.getInteger("clustermanager.serviceagents.checks.maxpollingretries[@value]", -1);
		
		log.debug("clusterName -> " + clusterName);
		log.debug("serviceAgentPollingPolicy -> " + serviceAgentPollingPolicy);
		log.debug("serviceAgentPollingTimeout -> " + serviceAgentPollingTimeout);
		log.debug("serviceAgentMaxPollingRetries -> " + serviceAgentMaxPollingRetries);
		
		List<HierarchicalConfiguration<ImmutableNode>> agents = conf.configurationsAt("clustermanager.serviceagents.agent");
		for(HierarchicalConfiguration<ImmutableNode> agent : agents) {
			try {
				String commonFunctionsUrl = agent.getString("url[@value]");
				URL aU = new URL(commonFunctionsUrl);
				
				aU.getProtocol();
				aU.getHost();
				aU.getPort();
				
				RemoteConnector ck = new RemoteConnector(aU.getProtocol(), aU.getHost(), aU.getPort());
				ClusterManagerServiceCheck csac = new ClusterManagerServiceCheck(ck, serviceAgentPollingPolicy);
				
				log.debug(String.format("Register element with url '%s'", aU.toURI()));
				serviceAgentList.add(csac);
	
			}
			catch (MalformedURLException e) {
				log.error(e);
			}
		}
		
		System.setProperty("ews.name", clusterName);
		System.setProperty("ews.type", getServiceType().getValue());
	}

	private Logger log = null;
	
	private String clusterName = null;
	private String serviceAgentPollingPolicy = null;
	private Long serviceAgentPollingTimeout = null;
	private Integer serviceAgentMaxPollingRetries = null;
	private List<ClusterManagerServiceCheck> serviceAgentList = null;
	
	
	private Map<String, ServiceDescriptor> remoteServicesMap = null;


	


	

	
}
