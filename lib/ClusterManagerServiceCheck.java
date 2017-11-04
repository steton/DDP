package it.ddp.services.clustermanager;

import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;

import it.ddp.common.objects.ServiceStatusInfoInterface;
import it.ddp.common.remote.RemoteConnector;
import it.ddp.services.core.AbstractService.ServiceStatus;
import it.ddp.services.core.AbstractService.ServiceType;

public class ClusterManagerServiceCheck extends AbstractStrategy {
	
	public ClusterManagerServiceCheck(RemoteConnector rc, String intervallPolicies) throws Exception {
		super(rc, intervallPolicies);
		
		log = Logger.getLogger(ClusterManagerServiceCheck.class);
		
		status = ServiceStatus.UNKNOWN;
		timeoutErrorAfterOkCount = -1;
		genericErrorAfterOkCount = -1;
	}

	@Override
	public void executeStrategy() {
		if(status.equals(ServiceStatus.INVALID_SERVICE_STATUS))
			return;
		try {
			ServiceStatusInfoInterface el = getServiceStatus(getConnector());
		
			if(el != null) {
				log.debug(String.format("Connected to element '%s' of type '%s' with url '%s'", el.getName(), el.getType(), getConnector().getBaseURI()));
				if(el.getType().equals(ServiceType.SERVICEAGENT.getValue()) ) {
					log.info(String.format("ServiceAgent '%s'", el.getName()));
					status = ServiceStatus.OK;
					lastStatusObject = el;
					timeoutErrorAfterOkCount = 0;
				}
				else {
					lastStatusObject = null;
					status = ServiceStatus.INVALID_SERVICE_STATUS;
					log.info(String.format("Element '%s' is not a '%s'. Skip.", el.getName(), el.getType()));
				}
			}
		}
		catch(TimeoutException e) {
			log.error(e);
			if(timeoutErrorAfterOkCount >= 0 && timeoutErrorAfterOkCount <= maxTimeoutErrorAfterOk) {
				timeoutErrorAfterOkCount += 1;
			}
		}
		catch (Exception e) {
			log.error(e);
			if(genericErrorAfterOkCount >= 0 && genericErrorAfterOkCount <= maxGenericErrorAfterOk) {
				genericErrorAfterOkCount += 1;
			}
		}
		
		
		if(timeoutErrorAfterOkCount > 0) {
			if(timeoutErrorAfterOkCount <= maxTimeoutErrorAfterOk) {
				status = ServiceStatus.OK_CONN_TIMEOUT;
				log.debug(String.format("Status change to '%s' (count %d)", status.name(), timeoutErrorAfterOkCount));
			}
			else {
				status = ServiceStatus.NOK_CONNECTION_TIMEOUT;
				log.debug(String.format("Status change to '%s'", status.name()));
			}
		}
		
		if(genericErrorAfterOkCount > 0) {
			if(genericErrorAfterOkCount <= maxGenericErrorAfterOk) {
				status = ServiceStatus.OK_GEN_ERROR;
				log.debug(String.format("Status change to '%s' (count %d)", status.name(), timeoutErrorAfterOkCount));
			}
			else {
				status = ServiceStatus.NOK_GEN_ERROR;
				genericErrorAfterOkCount = maxGenericErrorAfterOk;
				log.debug(String.format("Status change to '%s'", status.name()));
			}
		}
		
		log.info(String.format("%s status is '%s'", getRemoteServiceName(), status.name()));
	}
	
	public ServiceStatus getStatus() {
		return status;
	}
	
	public String getRemoteServiceName() {
		if(lastStatusObject == null)
			return "(UNKNOWN [" + getConnector().getBaseURI() + "])";
		
		return lastStatusObject.getName();
	}
	
	public String getRemoteServiceType() {
		if(lastStatusObject == null)
			return "(UNKNOWN)";
		
		return lastStatusObject.getType();
	}
	
	public String getRemoteServiceWebServiceHost() {
		if(lastStatusObject == null)
			return "(UNKNOWN)";
		
		return lastStatusObject.getWebServiceHost();
	}
	
	public String getRemoteServiceWebServicePort() {
		if(lastStatusObject == null)
			return "(UNKNOWN)";
		
		return "" + lastStatusObject.getWebServicePort();
	}
	
	
	private Logger log = null;
	
	private ServiceStatus status = null;
	private Integer timeoutErrorAfterOkCount = null;
	private Integer genericErrorAfterOkCount = null;
	
	private ServiceStatusInfoInterface lastStatusObject = null;

	private Integer maxTimeoutErrorAfterOk = 3;
	private Integer maxGenericErrorAfterOk = 3;
	
}
