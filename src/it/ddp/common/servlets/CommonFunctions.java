package it.ddp.common.servlets;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import it.ddp.applications.AbstractService;
import it.ddp.applications.InternalProcessRegistry;
import it.ddp.common.objects.ServiceStatusInfo;

@Path("common")
public class CommonFunctions {
	
	@GET
	@Path("info")
	@Produces(MediaType.APPLICATION_JSON)
	public ServiceStatusInfo getInfo() {
			
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		ObjectName oson = null;
		ObjectName rton = null;
		
		Long firstCpuTimeSample = null;
		Long firstDateSample = null;
		
		String processPid = null;
		String hostName = null;
		Double systemCpuLoad = null;
		Double processCpuLoad = null;
		Double cpuUsage = null;
		Long memTotal = null;
		Long memFree = null;
		Long swapTotal = null;
		Long swapFree = null;
		Integer availableProcessors = null;
		String sysArch = null;
		String osName = null;
		String osVersion = null;
		
		try {
			oson = ObjectName.getInstance("java.lang:type=OperatingSystem");
			rton = ObjectName.getInstance("java.lang:type=Runtime");
			
			// ---------------------------------------------------
			// Set first cpuTime sample to calculate cpuUsage.
			try {
				firstCpuTimeSample = (Long) mBeanServer.getAttribute(oson, "ProcessCpuTime");
				firstDateSample = System.nanoTime();
			}
			catch (Exception e) {
				firstCpuTimeSample = 0L;
			}
			// ---------------------------------------------------
		
		
			// ------------
			try {
				String pidAndHost = (String) mBeanServer.getAttribute(rton, "Name");
				processPid = pidAndHost.split("@")[0];
				hostName = pidAndHost.split("@")[1];
			}
			catch (Exception e) {
				processPid = "Unknown";
				hostName = "Unknown";
			}
			
			// ------------
			try {
				systemCpuLoad = (Double)mBeanServer.getAttribute(oson, "SystemCpuLoad");
			}
			catch (Exception e) {
				systemCpuLoad = -1.0;
			}
			
			// ------------
			try {
				processCpuLoad = (Double)mBeanServer.getAttribute(oson, "ProcessCpuLoad");
			}
			catch (Exception e) {
				processCpuLoad = -1.0;
			}
			
			// ------------
			try {
				memTotal = (Long)mBeanServer.getAttribute(oson, "TotalPhysicalMemorySize");
			}
			catch (Exception e) {
				memTotal = -1L;
			}
			
			// ------------
			try {
				memFree = (Long)mBeanServer.getAttribute(oson, "FreePhysicalMemorySize");
			}
			catch (Exception e) {
				memFree = -1L;
			}
			
			// ------------
			try {
				swapTotal = (Long)mBeanServer.getAttribute(oson, "TotalSwapSpaceSize");
			}
			catch (Exception e) {
				swapTotal = -1L;
			}
			
			// ------------
			try {
				swapFree = (Long)mBeanServer.getAttribute(oson, "FreeSwapSpaceSize");
			}
			catch (Exception e) {
				swapFree = -1L;
			}
			
			// ------------
			try {
				Long secondCpuTimeSample = (Long)mBeanServer.getAttribute(oson, "ProcessCpuTime");
				Long secondDateSample = System.nanoTime();
				
				cpuUsage = ((Double)(100.0 * (secondCpuTimeSample * 1.0 - firstCpuTimeSample * 1.0) / (secondDateSample * 1.0 - firstDateSample * 1.0)))/((Integer)mBeanServer.getAttribute(oson, "AvailableProcessors"));
				
			} catch (Exception e) {
				cpuUsage = -1.0;
			}
			
			// ------------
			try {
				availableProcessors = (Integer)mBeanServer.getAttribute(oson, "AvailableProcessors");
				
			} catch (Exception e) {
				availableProcessors = -1;
			}
			
			try {
				sysArch = (String) mBeanServer.getAttribute(oson, "Arch");
			} catch (Exception e) {
				sysArch = "Unknown";
			}
			
			try {
				osName = (String) mBeanServer.getAttribute(oson, "Name");
			} catch (Exception e) {
				osName = "Unknown";
			}
			
			try {
				osVersion = (String) mBeanServer.getAttribute(oson, "Version");
			} catch (Exception e) {
				osVersion = "Unknown";
			}	
			
		}
		catch (MalformedObjectNameException e1) {
			e1.printStackTrace();
		}
		
		ServiceStatusInfo s = new ServiceStatusInfo(System.getProperty("ews.name"));
		
		s.setType(System.getProperty("ews.type"));
		s.setServiceCommonFunctionsUrl(System.getProperty("ews.commonFunctionsUrl"));
		
		s.setHostLoad(systemCpuLoad);
		s.setProcessCpuLoad(processCpuLoad);
		s.setCpuUsage(cpuUsage);
	    s.setHostname(hostName);
	    s.setProcessPid(processPid);
		s.setCpuNumber(availableProcessors);
		s.setMemTotal(memTotal);
		s.setMemFree(memFree);
		s.setSwapTotal(swapTotal);
		s.setSwapFree(swapFree);
		s.setSysArch(sysArch);
		s.setOsName(osName);
		s.setOsVersion(osVersion);
		
		InternalProcessRegistry<AbstractService> ipr = InternalProcessRegistry.getInstance();
		AbstractService cm = ipr.getService();		
		s.setWebServiceHost(cm.getLocalWebServiceHost());
		s.setWebServicePort(cm.getLocalWebSercivePort());
		
		s.setResult("OK");
		return s;
	}
	
	
	@POST
	@Path("enable")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public String enablePost(Object enable) throws InterruptedException {
	    return "it worked!";
	}
	
  
	@GET
	@Path("param")
	@Produces(MediaType.TEXT_PLAIN)
	public String paramMethod(@QueryParam("name") String name) {
		return "Hello, " + name;
	}
	  
	@GET
	@Path("path/{var}")
	@Produces(MediaType.TEXT_PLAIN)
	public String pathMethod(@PathParam("var") String name) {
		return "Hello, " + name;
	}
  
	@POST
	@Path("post")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.TEXT_HTML)
	public String postMethod(@FormParam("name") String name) {
		return "<h2>Hello, " + name + "</h2>";
	}
}