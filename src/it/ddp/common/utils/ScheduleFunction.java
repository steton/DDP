package it.ddp.common.utils;

@FunctionalInterface
public interface ScheduleFunction {
	public void apply() throws Exception;
}
