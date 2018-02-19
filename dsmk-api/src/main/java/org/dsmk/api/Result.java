package org.dsmk.api;

public class Result {
	
	public static enum Status{
		OK,NOT_OK;
	}
	
	public static Result OK = new Result(Status.OK,"common.status.ok","common.msg.success");
	public static Result NOT_OK = new Result(Status.NOT_OK,"common.status.notok","common.msg.failure");
	
	private Status status;
	private String code;
	private String msg;
	
	
	public Result(Status sts,String code,String msg) {
		this.status = sts;
		this.code = code;
		this.msg = msg;
	}
	
	public boolean isOK() {
		return status == Status.OK;
	}
	
	public boolean isNotOK() {
		return status == Status.NOT_OK;
	}

	public Status getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getMsg() {
		return msg;
	}
	
	

}
