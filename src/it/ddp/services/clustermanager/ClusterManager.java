package it.ddp.services.clustermanager;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.ddp.common.objects.ServiceDescriptor;
import it.ddp.common.objects.ServiceDescriptorInterface;
import it.ddp.common.objects.ServiceStatusInfo;
import it.ddp.common.objects.ServiceStatusInfoInterface;
import it.ddp.common.remote.RemoteConnector;
import it.ddp.common.utils.SchedulerUtil;
import it.ddp.services.core.AbstractService;
import it.ddp.services.core.FunctionResult;
import it.ddp.services.core.InternalProcessRegistry;



public class ClusterManager extends AbstractService {

	public ClusterManager(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		log = Logger.getLogger(ClusterManager.class);
		
		configureWebServer();
		configureClusterManager();
		
		InternalProcessRegistry<ClusterManager> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		
		startInternalServices();
		
		startCommunicationServer();
	}
	
	
	public Map<String, ServiceDescriptor> getServiceLists() {
		return remoteServicesMap;
	}
	
	/**
	 * Register (or update) service.
	 */
	public FunctionResult registerService(ServiceDescriptor sd) {
		FunctionResult res = FunctionResult.UNKNOWN;
		
		if(remoteServicesMap.containsKey(sd.name)) {
			res = FunctionResult.DATA_UPDATE;
		}
		else {
			res = FunctionResult.OK;
		}
		
		log.debug(String.format("%s service (%s)", (res.equals(FunctionResult.OK)?"Configure":"Update"),sd));
		
		remoteServicesMap.put(sd.name, sd);
		
		return res;
	}
	
	
	public void close() throws Exception {
		log.debug(String.format("Stopping ClusterManager '%s'.", clusterName));
		
		/*
		for(ClusterManagerServiceCheck sac : serviceAgentList) {
			sac.enable(false);
		}
		
		
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
	
	@Override
	protected void executeServiceStrategy() {
		log.debug("Activate cluster manager.");
		
		try {
			//Every 5 seconds a task check is some new service is connected to Cluster manager
			newServiceCheckScheduler = new SchedulerUtil(newServiceCheckSchedulerPolicy, () -> {
				Long nowTs = System.currentTimeMillis();
				
				log.debug("Check for service status updating.");
				for(String connServ : remoteServicesMap.keySet()) {
					ServiceDescriptorInterface sd = (ServiceDescriptorInterface)remoteServicesMap.get(connServ);
					if(!registeredServicesMap.containsKey(connServ)) {
						log.info(String.format("Found new connected service '%s'", connServ));
						registeredServicesMap.put(connServ,new RemoteConnector("https", sd.getWebServerHost(), sd.getWebServerPort()));
						registeredServiceStatusMap.put(connServ, ServiceStatus.NOT_YET_CONFIGURED_IN_CLUSTERMANAGER);
						schedulerServicesMap.put(connServ, new SchedulerUtil(serviceAgentPollingPolicy, () -> {
							log.info(String.format("Start ClusterManager check task for service '%s'", connServ));
							try {
								RemoteConnector rc = registeredServicesMap.get(connServ);
								ServiceStatusInfoInterface el = getServiceStatus(rc);
								
								if(el != null) {
									log.debug(String.format("Connected to element '%s' of type '%s' with url '%s'", el.getName(), el.getType(), rc.getBaseURI()));
									registeredServiceStatusMap.put(connServ, ServiceStatus.OK);
								}
								else {
									registeredServiceStatusMap.put(connServ, ServiceStatus.INVALID_SERVICE_STATUS);
								}
							}
							catch(TimeoutException e) {
								log.error(e);
								registeredServiceStatusMap.put(connServ, ServiceStatus.NOK_CONNECTION_TIMEOUT);
								
							}
							catch (Exception e) {
								log.error(e);
								registeredServiceStatusMap.put(connServ, ServiceStatus.NOK_GEN_ERROR);
							}
							
							log.info(String.format("%s status is '%s'", connServ, registeredServiceStatusMap.get(connServ).name()));
						}));
						schedulerServicesMap.get(connServ).enable(true);
					}
					if(nowTs - sd.getLastUpdateTime() > serviceAgentPollingTimeout) {
						log.info(String.format("Agent '%s' doesn't update its status from %d ms.", connServ, nowTs - sd.getLastUpdateTime()));
						registeredServiceStatusMap.put(connServ, ServiceStatus.NOK_CONNECTION_TIMEOUT);
					}
				}
			});
			newServiceCheckScheduler.enable(true);
			
			new SchedulerUtil(newServiceCheckSchedulerPolicy, () -> {
				try {
					for(String s : registeredServiceStatusMap.keySet()) {
						log.info(String.format("Service '%s' has status '%s'", s, registeredServiceStatusMap.get(s).name()));
					}
				}
				catch(Exception e) {
					log.error(e);
				}
			}).enable(true);
		}
		catch (Exception e) {
			log.fatal(e);
		}
	}
	
	// ------------------------------------------------------------------
	// -- P R I V A T E   M E T H O D S ---------------------------------
	// ------------------------------------------------------------------	
	
	private void configureClusterManager() throws Exception {
		XMLConfiguration conf = getConfig();
		
		remoteServicesMap = new HashMap<>();
		registeredServicesMap = new HashMap<>();
		registeredServiceStatusMap = new HashMap<>();
		schedulerServicesMap = new HashMap<>();
		
		clusterName = conf.getString("clustermanager[@name]", NONE_STRING);
		serviceAgentPollingPolicy = conf.getString("clustermanager.serviceagents.checks.pollingpolicy[@value]", "");
		serviceAgentPollingTimeout = conf.getLong("clustermanager.serviceagents.checks.pollingtimeout[@value]", -1L);
		serviceAgentMaxPollingRetries = conf.getInteger("clustermanager.serviceagents.checks.maxpollingretries[@value]", -1);
		
		log.debug("clusterName -> " + clusterName);
		log.debug("serviceAgentPollingPolicy -> " + serviceAgentPollingPolicy);
		log.debug("serviceAgentPollingTimeout -> " + serviceAgentPollingTimeout);
		log.debug("serviceAgentMaxPollingRetries -> " + serviceAgentMaxPollingRetries);
		
		
		/*
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
		*/
		
		System.setProperty("ews.name", clusterName);
		System.setProperty("ews.type", getServiceType().getValue());
	}
	
	
	private ServiceStatusInfoInterface getServiceStatus(RemoteConnector r) throws URISyntaxException, IOException,  InterruptedException, TimeoutException, ExecutionException {
		
		String json = null;
		
		try {
			log.info(String.format("Retreive information from '%s'", r.getBaseURI().toString()));
			json = r.post("/servlet/common/info", "");
		}
		catch (URISyntaxException | IOException | InterruptedException | TimeoutException | ExecutionException e) {
			log.error(e);
			json = null;
			throw e;
		}
		
		ServiceStatusInfoInterface s = null;
		
		if(json != null && !json.isEmpty()) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				s = mapper.readValue(json, ServiceStatusInfo.class);
				if(s != null) {
					if(s instanceof ServiceStatusInfoInterface) {
						log.info(String.format("Got information from element '%s' of type '%s'", s.getName(), s.getType()));
					}
					else {
						log.debug(String.format("Invalid element '%s' of type '%s'.", s.getName(), s.getType()));
					}
				}
				else {
					log.debug(String.format("Invalid 'null' element."));
				}
			}
			catch (IOException e) {
				log.error(e);
				throw e;
			}
		}
		
		return s;
	}

	private Logger log = null;
	
	private String clusterName = null;
	private String serviceAgentPollingPolicy = null;
	private Long serviceAgentPollingTimeout = null;
	private Integer serviceAgentMaxPollingRetries = null;
	//private List<ClusterManagerServiceCheck> serviceAgentList = null;
	
	
	private Map<String, ServiceDescriptor> remoteServicesMap = null;
	private Map<String, RemoteConnector> registeredServicesMap = null;
	private Map<String, SchedulerUtil> schedulerServicesMap = null;
	private Map<String, ServiceStatus> registeredServiceStatusMap = null;
	
	private SchedulerUtil newServiceCheckScheduler = null;
	private final String newServiceCheckSchedulerPolicy = "0/5 * * * * *";
	
}
