package it.ddp.common.servlets;

import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import it.ddp.common.objects.RemoteCallResult;
import it.ddp.common.objects.ServiceDescriptor;
import it.ddp.services.clustermanager.ClusterManager;
import it.ddp.services.core.FunctionResult;
import it.ddp.services.core.InternalProcessRegistry;

@Path("cm")
public class ClusterManagerFunctions {

	@GET
	@Path("servicelist")
	@Produces(MediaType.APPLICATION_JSON)
	public List<ServiceDescriptor> getServiceList() {
		InternalProcessRegistry<ClusterManager> ipr = InternalProcessRegistry.getInstance();
		ClusterManager cm = ipr.getService();
		
		return Collections.list(Collections.enumeration(cm.getServiceLists().values()));
	}
	
	
	@GET
	@Path("registerservice")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public RemoteCallResult registerService(ServiceDescriptor service) {
		InternalProcessRegistry<ClusterManager> ipr = InternalProcessRegistry.getInstance();
		ClusterManager cm = ipr.getService();
		
		FunctionResult fc = cm.registerService(service);
		RemoteCallResult rcr = new RemoteCallResult(fc.getTextCode(), fc.getTextResult());
		return rcr;
	}
}
