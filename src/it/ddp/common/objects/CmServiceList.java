package it.ddp.common.objects;


import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CmServiceList implements CmServiceListInterface {
	
	@JsonCreator
	public CmServiceList(@JsonProperty("serviceList")Map<String, ServiceDescriptor> serviceList) {
		this.serviceList = serviceList;
	}


	@Override
	public Map<String, ServiceDescriptor> getServiceList() {
		return serviceList;
	}

	public void setServiceList(Map<String, ServiceDescriptor> serviceList) {
		this.serviceList = serviceList;
	}
	
	private Map<String, ServiceDescriptor> serviceList = null;

}
