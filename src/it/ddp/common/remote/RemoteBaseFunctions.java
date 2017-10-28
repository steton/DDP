package it.ddp.common.remote;

import it.ddp.common.objects.ServiceStatusInfoInterface;

public interface RemoteBaseFunctions {
	public ServiceStatusInfoInterface getServiceStatus(RemoteConnector r) throws Exception;
}
