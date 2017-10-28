package it.ddp.applications;

public enum FunctionResult {
	
	OK(0, "SUCCESSFUL"),
	DATA_UPDATE(100, "DATA UPDATE"),
	INTERNAL_ERROR(-1, "INTERNAL ERROR"),
	UNKNOWN(Integer.MIN_VALUE, "UNKNOWN RESULT");
	
	
	
	private FunctionResult(Integer rc, String rt) {
		this.textCode = rc;
		this.textResult = rt;
	}
	
	public Integer getTextCode() {
		return textCode;
	}
	
	public String getTextResult() {
		return textResult;
	}

	private Integer textCode = null;
	private String textResult = null;
}
