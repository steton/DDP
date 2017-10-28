package it.ddp.applications;

public final class InternalProcessRegistry<T> {
	
	
	@SuppressWarnings("unchecked")
	public static <T>InternalProcessRegistry<T> getInstance() {
		synchronized(InternalProcessRegistry.class) {
			if(instance == null) {
				instance = new InternalProcessRegistry<>();
			}
		}
		return (InternalProcessRegistry<T>) instance;
	}
	
	public boolean isAgentDefined() {
		return (this.agent != null);
	}

	public void subscribeAgent(T agent) {
		this.agent = agent;
	}
	
	public T getService() {
		return agent;
	}

	
	private static InternalProcessRegistry<?> instance = null;

	private T agent = null;
}
