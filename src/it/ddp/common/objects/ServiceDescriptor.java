package it.ddp.common.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceDescriptor implements ServiceDescriptorInterface {
	
	@JsonCreator
	public ServiceDescriptor(@JsonProperty("name")String name, @JsonProperty("type")String type, 
			@JsonProperty("webServerHost")String webServerHost, 
			@JsonProperty("webServerPort")Integer webServerPort,
			@JsonProperty("lastUpdateTime")Long lastUpdateTime) {
		this.name = name;
		this.type = type;
		this.webServerHost = webServerHost;
		this.webServerPort = webServerPort;
		this.lastUpdateTime = lastUpdateTime;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getWebServerHost() {
		return webServerHost;
	}
	
	public void setWebServerHost(String webServerHost) {
		this.webServerHost = webServerHost;
	}
	
	@Override
	public Integer getWebServerPort() {
		return webServerPort;
	}
	
	public void setWebServerPort(Integer webServerPort) {
		this.webServerPort = webServerPort;
	}
	
	@Override
	public Long getLastUpdateTime() {
		return lastUpdateTime;
	}

	public void setLastUpdateTime(Long lastUpdateTime) {
		this.lastUpdateTime = lastUpdateTime;
	}



	public String name = null;
	public String type = null;
	public String webServerHost = null;
	public Integer webServerPort = null;
	public Long lastUpdateTime = null;
}
