package it.ddp.common.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteCallResult implements RemoteCallResultInterface {

	@JsonCreator
	public RemoteCallResult(@JsonProperty("resultCode")Integer resultCode, @JsonProperty("resultText")String resultText) {
		this.resultCode = resultCode;
		this.resultText = resultText;
	}
	
	
	@Override
	public Integer getResultCode() {
		return resultCode;
	}
	
	
	public void setResultCode(Integer resultCode) {
		this.resultCode = resultCode;
	}
	
	@Override
	public String getResultText() {
		return resultText;
	}
	
	
	public void setResultText(String resultText) {
		this.resultText = resultText;
	}



	private Integer resultCode = null;
	private String resultText = null;

}
