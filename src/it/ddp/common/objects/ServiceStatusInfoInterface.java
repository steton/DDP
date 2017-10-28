package it.ddp.common.objects;

public interface ServiceStatusInfoInterface {
	public String getName();
	public String getType();
	public String getOsName();
	public String getSysArch();
	public String getOsVersion();
	public String getResult();
	public String getHostname();
	public Integer getCpuNumber();
	public Double getHostLoad();
	public Double getProcessCpuLoad();
	public Double getCpuUsage();
	public Long getMemTotal();
	public Long getMemFree();
	public Long getSwapTotal();
	public Long getSwapFree();
	public String getProcessPid();
	public String getServiceCommonFunctionsUrl();
	public String getWebServiceHost();
	public Integer getWebServicePort();
}
