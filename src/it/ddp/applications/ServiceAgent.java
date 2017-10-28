package it.ddp.applications;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DaemonExecutor;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.log4j.Logger;

public class ServiceAgent extends AbstractService {
	
	public class ProcessDescriptor {
		
		public ProcessDescriptor(CommandLine cmd, Boolean enabled) {
			super();
			this.commandLine = cmd;
			this.enabled = enabled;
			
			//ExecuteWatchdog watchdog = new ExecuteWatchdog(30000);
			resultHandler = new DefaultExecuteResultHandler();
			executor = new DaemonExecutor();
			executor.setExitValue(1);
			//executor.setWatchdog(watchdog);
		}
		
		public CommandLine getExecutor() {
			return commandLine;
		}
		
		public Boolean isEnabled() {
			return enabled;
		}
		
		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public void execute() {
			if(!enabled) {
				log.debug("Process is not enabled. Skip");
				return;
			}
			
			try {
				log.debug("Execute command " + commandLine.getExecutable());
				executor.execute(commandLine, resultHandler);
			}
			catch (ExecuteException e) {
				log.error(e);
			}
			catch (IOException e) {
				log.error(e);
			}
		}
		
		public int getExecutionResult() {
			int res = -1;
			try {
				resultHandler.waitFor();
			}
			catch (InterruptedException e) {
				log.error(e);
			}
			
			res = resultHandler.getExitValue();
			return res;
		}

		private CommandLine commandLine = null;
		private Executor executor = null;
		private DefaultExecuteResultHandler resultHandler = null;
		private Boolean enabled = null;
	}

	public ServiceAgent(File xmlConfigFile) throws Exception {
		super(xmlConfigFile);
		
		log = Logger.getLogger(ServiceAgent.class);
		log.debug("ServiceAgent init...");
		
		configureWebServer();
		configureServiceAgent();
		
		for(ProcessDescriptor p : agentsChilds) {
			p.execute();
		}
		
		InternalProcessRegistry<ServiceAgent> ir = InternalProcessRegistry.getInstance();
		ir.subscribeAgent(this);
		
        startCommunicationServer();
	}
	
	private void configureServiceAgent() throws ConfigurationException {
		XMLConfiguration conf = getConfig();
		
		agentsChilds = new ArrayList<>();
		
		serviceAgentName = conf.getString("serviceagent[@name]", NONE_STRING);
		
		List<HierarchicalConfiguration<ImmutableNode>> services = conf.configurationsAt("serviceagent.service");
		for(HierarchicalConfiguration<ImmutableNode> service : services) {
			String command = service.getString("[@command]");
			Boolean active = service.getString("[@active]").toUpperCase().trim().equals("TRUE");
			
			CommandLine cmdLine = new CommandLine(command);
			
			String aa = "";
			List<HierarchicalConfiguration<ImmutableNode>> args = service.configurationsAt("arg");
			for(HierarchicalConfiguration<ImmutableNode> arg : args) {
				String a = arg.getString("[@value]");
				aa = aa + a + " ";
				cmdLine.addArgument(a);
			}
			log.info(String.format("EXEC(%s): %s %s", "" + active, command, aa));
			
			agentsChilds.add(new ProcessDescriptor(cmdLine, active));
		}
		
		System.setProperty("ews.name", serviceAgentName);
		System.setProperty("ews.type", TYPE);
	}
	
	private Logger log = null;
	
	private List<ProcessDescriptor> agentsChilds = null;
	
	private String serviceAgentName = null;
	public static final String TYPE = "SERVICEAGENT";
}
