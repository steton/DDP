package it.ddp.common.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceStatusInfo implements ServiceStatusInfoInterface {
	
	private String name = null;
	private String type = null;
	private String result = null;
	private String serviceCommonFunctionsUrl = null;
	private String hostname = null;
	private String processPid = null;
	private Integer cpuNumber = null;
	private Double hostLoad = null;
	private String sysArch = null;
	private String osName = null;
	private String osVersion = null;
	private Double processCpuLoad = null;
	private Double cpuUsage = null;
	private Long memTotal = null;
	private Long memFree = null;
	private Long swapTotal = null;
	private Long swapFree = null;
	private String webServiceHost = null;
	private Integer webServicePort = null;
	
	
	@JsonCreator
	public ServiceStatusInfo(@JsonProperty("name")String name, @JsonProperty("type")String type, @JsonProperty("result")String result, @JsonProperty("serviceCommonFunctionsUrl")String serviceCommonFunctionsUrl, @JsonProperty("hostname")String hostname,
			@JsonProperty("processPid")String processPid, @JsonProperty("cpuNumber")Integer cpuNumber, @JsonProperty("hostLoad")Double hostLoad, @JsonProperty("sysArch")String sysArch, @JsonProperty("osName")String osName, @JsonProperty("osVersion")String osVersion,
			@JsonProperty("processCpuLoad")Double processCpuLoad, @JsonProperty("cpuUsage")Double cpuUsage, @JsonProperty("memTotal")Long memTotal, @JsonProperty("memFree")Long memFree, @JsonProperty("swapTotal")Long swapTotal, @JsonProperty("swapFree")Long swapFree,
			@JsonProperty("webServiceHost")String webServiceHost, @JsonProperty("webServicePort")Integer webServicePort) {
		this.name = name;
		this.type = type;
		this.result = result;
		this.serviceCommonFunctionsUrl = serviceCommonFunctionsUrl;
		this.hostname = hostname;
		this.processPid = processPid;
		this.cpuNumber = cpuNumber;
		this.hostLoad = hostLoad;
		this.sysArch = sysArch;
		this.osName = osName;
		this.osVersion = osVersion;
		this.processCpuLoad = processCpuLoad;
		this.cpuUsage = cpuUsage;
		this.memTotal = memTotal;
		this.memFree = memFree;
		this.swapTotal = swapTotal;
		this.swapFree = swapFree;
		this.webServiceHost = webServiceHost;
		this.webServicePort = webServicePort;
	}

	public ServiceStatusInfo(String n) {
		this.name = n;
	}
	
	
	@Override
	public String getWebServiceHost() {
		return webServiceHost;
	}

	public void setWebServiceHost(String webServiceHost) {
		this.webServiceHost = webServiceHost;
	}

	@Override
	public Integer getWebServicePort() {
		return webServicePort;
	}

	public void setWebServicePort(Integer webServicePort) {
		this.webServicePort = webServicePort;
	}

	@Override
	public String getName() {
		return this.name;
	}
	
	
	@Override
	public String getType() {
		return type;
	}
	

	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getOsName() {
		return osName;
	}

	public void setOsName(String osName) {
		this.osName = osName;
	}
	
	@Override
	public String getSysArch() {
		return sysArch;
	}

	public void setSysArch(String sysArch) {
		this.sysArch = sysArch;
	}

	@Override
	public String getOsVersion() {
		return osVersion;
	}

	public void setOsVersion(String osVersion) {
		this.osVersion = osVersion;
	}

	@Override
	public String getResult() {
		return result;
	}
	
	public void setResult(String result) {
		this.result = result;
	}
	
	@Override
	public String getHostname() {
		return hostname;
	}
	
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	@Override
	public Integer getCpuNumber() {
		return cpuNumber;
	}
	
	public void setCpuNumber(Integer cpuNumber) {
		this.cpuNumber = cpuNumber;
	}
	
	@Override
	public Double getHostLoad() {
		return hostLoad;
	}
	
	public void setHostLoad(Double hostLoad) {
		this.hostLoad = hostLoad;
	}

	public void setProcessCpuLoad(Double processCpuLoad) {
		this.processCpuLoad = processCpuLoad;		
	}

	@Override
	public Double getProcessCpuLoad() {
		return processCpuLoad;
	}

	public void setCpuUsage(Double cpuUsage) {
		this.cpuUsage = cpuUsage;
	}

	@Override
	public Double getCpuUsage() {
		return cpuUsage;
	}

	public void setMemTotal(Long memTotal) {
		this.memTotal = memTotal;
	}

	@Override
	public Long getMemTotal() {
		return memTotal;
	}

	public void setMemFree(Long memFree) {
		this.memFree = memFree;
	}

	@Override
	public Long getMemFree() {
		return memFree;
	}

	public void setSwapTotal(Long swapTotal) {
		this.swapTotal = swapTotal;
	}

	@Override
	public Long getSwapTotal() {
		return swapTotal;
	}

	public void setSwapFree(Long swapFree) {
		this.swapFree = swapFree;
	}

	@Override
	public Long getSwapFree() {
		return swapFree;
	}

	public void setProcessPid(String processPid) {
		this.processPid = processPid;
	}

	@Override
	public String getProcessPid() {
		return processPid;
	}

	@Override
	public String getServiceCommonFunctionsUrl() {
		return serviceCommonFunctionsUrl;
	}

	public void setServiceCommonFunctionsUrl(String serviceCommonFunctionsUrl) {
		this.serviceCommonFunctionsUrl = serviceCommonFunctionsUrl;
	}
	
}
