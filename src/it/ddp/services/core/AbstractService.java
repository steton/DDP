package it.ddp.services.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.ddp.common.objects.RemoteCallResult;
import it.ddp.common.objects.RemoteCallResultInterface;
import it.ddp.common.objects.ServiceDescriptor;
import it.ddp.common.remote.RemoteConnector;
import it.ddp.main.Starter;

public abstract class AbstractService {
	
	
	public enum ServiceType {
		CLUSTERMANAGER("CLUSTERMANAGER"),
		SERVICEAGENT("SERVICEAGENT"),
		PRODUCER("PRODUCER"),
		CONSUMER("CONSUMER");
		
		private ServiceType(String type) {
			this.ServiceType = type;
		}
		
		public String getValue() {
			return this.ServiceType;
		}
		
		public static ServiceType getServiceTypeByString(String t) {
			if(t.equals(CLUSTERMANAGER.getValue()))
				return CLUSTERMANAGER;
			if(t.equals(SERVICEAGENT.getValue()))
				return SERVICEAGENT;
			if(t.equals(PRODUCER.getValue()))
				return PRODUCER;
			if(t.equals(CONSUMER.getValue()))
				return CONSUMER;
			return null;
		}
		
		private String ServiceType = null;
	};
	
	
 	public AbstractService(File xmlConfigFile) throws ParseException, ConfigurationException, IOException {
		
		log = Logger.getLogger(AbstractService.class);
		
		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
		    .configure(params.xml()
		    .setFileName(xmlConfigFile.getAbsolutePath())
		    .setValidating(false));

		config = builder.getConfiguration();
		
		String workDir = config.getString("application.workdir[@value]", Starter.NONE_STRING);
		if(workDir==null || workDir.equals(Starter.NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'value' in tag 'application.workDir'.");
			log.error(e);
			throw e;
		}
		File workDirFile = new File(workDir);
		if(workDirFile == null || !workDirFile.exists() || !workDirFile.isDirectory() || !workDirFile.canRead()) {
			ConfigurationException e = new ConfigurationException(String.format("Directory '%s' does not exists or it is not an existing readable directory.", workDir));
			log.error(e);
			throw e;
		}
		
		String webServerHost = config.getString("application.webserver[@host]", Starter.NONE_STRING);
		if(webServerHost==null || webServerHost.equals(Starter.NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'host' in tag 'application.webserver'.");
			log.error(e);
			throw e;
		}
		
		try {
			if(InetAddress.getByName(webServerHost).isMulticastAddress()) {
				ConfigurationException e = new ConfigurationException(String.format("Bad host address '%s'", webServerHost));
				log.error(e);
				throw e;
			}
		}
		catch (UnknownHostException e) {
			log.error(e);
			throw e;
		}
		
		String webServerPort = config.getString("application.webserver[@port]", Starter.NONE_STRING);
		if(webServerPort==null || webServerPort.equals(Starter.NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'value' in tag 'application.webServerPort'.");
			log.error(e);
			throw e;
		}
		
		if(webServerPort==null || Integer.parseInt(webServerPort) < 1024) {
			throw new ConfigurationException("Missing or bad required valid URL in tag 'application.webserverport[@value]'.");
		}
		
		
		String keyStore = config.getString("application.keystore[@file]", NONE_STRING);
		if(keyStore==null || keyStore.equals(NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'file' in tag 'application.keystore'.");
			log.error(e);
			throw e;
		}
		File keyStoreFile = new File(keyStore);
		if(keyStoreFile == null || !keyStoreFile.exists() || !keyStoreFile.isFile() || !workDirFile.canRead()) {
			ConfigurationException e = new ConfigurationException(String.format("FileStore '%s' does not exists or it is not an existing readable file.", keyStore));
			log.error(e);
			throw e;
		}
		
		String keyStorePassword = config.getString("application.keystore[@passwd]", NONE_STRING);
		if(keyStorePassword==null || keyStorePassword.equals(NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'passwd' in tag 'application.keystore'.");
			log.error(e);
			throw e;
		}
		
        
        File webBaseDir = new File(System.getProperty("ews.webbase", workDirFile.getAbsolutePath() + File.separator + "WebContent"));
        if(!webBaseDir.exists()) {
            throw new FileNotFoundException(webBaseDir.getAbsolutePath());
        }
                
        String libPath = System.getProperty("ews.lib", workDirFile.getAbsolutePath() + File.separator + "lib");
        File libPathDir = new File(libPath);
        if(!libPathDir.exists() || !libPathDir.isDirectory() || !libPathDir.canRead()) {
            throw new FileNotFoundException(libPathDir.getAbsolutePath());
        }
        
        String etcPath = System.getProperty("ews.etc", workDirFile.getAbsolutePath() + File.separator + "etc");
        File etcPathDir = new File(etcPath);
        if(!etcPathDir.exists() || !etcPathDir.isDirectory() || !etcPathDir.canRead()) {
            throw new FileNotFoundException(etcPathDir.getAbsolutePath());
        }
        
        System.setProperty("ews.home", workDirFile.getAbsolutePath());
        System.setProperty("ews.host", webServerHost);
        System.setProperty("ews.port", webServerPort);
        System.setProperty("ews.webbase", webBaseDir.getAbsolutePath());
        System.setProperty("ews.filestore.file", keyStoreFile.getAbsolutePath());
        System.setProperty("ews.filestore.password", keyStorePassword);
        System.setProperty("ews.etc", etcPathDir.getAbsolutePath());
        System.setProperty("ews.lib", libPathDir.getAbsolutePath());
        
        serverPoolMaxThreads = 500;
        serverServiceHost = webServerHost;
    	serverServicePort = webServerPort;
    	serverFunctionsPackage = SERVLETS_PACKAGE;
    	serverBasedirPath = workDirFile.getAbsolutePath();
    	serverWebBasePath = webBaseDir.getAbsolutePath();
    	serverEtcPath = etcPathDir.getAbsolutePath();
    	
    	serverKeyStoreFile = keyStoreFile.getAbsolutePath();
    	serverKeyStorePassword = keyStorePassword;
    	serverTrustStoreFile = keyStoreFile.getAbsolutePath();
    	serverTrustStorePassword = keyStorePassword;
    	
    	log.debug(String.format("serverPoolMaxThreads   : %s", serverPoolMaxThreads));
    	log.debug(String.format("serverServiceHost      : %s", serverServiceHost));
    	log.debug(String.format("serverServicePort      : %s", serverServicePort));
    	log.debug(String.format("serverFunctionsPackage : %s", serverFunctionsPackage));
    	log.debug(String.format("serverBasedirPath      : %s", serverBasedirPath));
    	log.debug(String.format("serverWebBasePath      : %s", serverWebBasePath));
    	log.debug(String.format("serverEtcPath          : %s", serverEtcPath));
    	log.debug(String.format("serverKeyStoreFile     : %s", serverKeyStoreFile));
    	log.debug(String.format("serverTrustStoreFile   : %s", serverTrustStoreFile));

	}
	
	
	protected XMLConfiguration getConfig() throws ConfigurationException {
		if(config == null) {
			throw new ConfigurationException("Undefined config file");
		}
		return config;
	}

	
	protected void configureWebServer() {
		
		QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(serverPoolMaxThreads);
 
        // Server
        server = new Server(threadPool);
        server.addBean(new ScheduledExecutorScheduler());
        
        MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.addBean(mbContainer);
        
        // Extra options
        server.setDumpAfterStart(false);
        server.setDumpBeforeStop(false);
        server.setStopAtShutdown(true);
        
     // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(Integer.parseInt(System.getProperty("ews.port", "" + serverServicePort)));
        http_config.setOutputBufferSize(32768);
        http_config.setRequestHeaderSize(8192);
        http_config.setResponseHeaderSize(8192);
        http_config.setSendServerVersion(true);
        http_config.setSendDateHeader(false);
        
        // -------------------------
 
        // === jetty-https.xml ===
        // SSL Context Factory
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(System.getProperty("ews.filestore.file", serverKeyStoreFile));
        sslContextFactory.setKeyStorePassword(System.getProperty("ews.filestore.password", serverKeyStorePassword));
        sslContextFactory.setKeyManagerPassword(System.getProperty("ews.filestore.password", serverKeyStorePassword));
        sslContextFactory.setTrustStorePath(System.getProperty("ews.filestore.file", serverTrustStoreFile));
        sslContextFactory.setTrustStorePassword(System.getProperty("ews.filestore.password", serverTrustStorePassword));
        sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
 
        // SSL HTTP Configuration
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());
 
        // SSL Connector
        ServerConnector sslConnector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(https_config));
        sslConnector.setPort(Integer.parseInt(System.getProperty("ews.port", "" + serverServicePort)));
        sslConnector.setHost(System.getProperty("ews.host", "" + serverServiceHost));
        server.addConnector(sslConnector);
        
        // === jetty-requestlog.xml ===
        NCSARequestLog requestLog = new NCSARequestLog();
        requestLog.setFilename(System.getProperty("ews.home") + File.separator + "log" + File.separator + "yyyy_mm_dd.request.log");
        requestLog.setFilenameDateFormat("yyyy_MM_dd");
        requestLog.setRetainDays(90);
        requestLog.setAppend(true);
        requestLog.setExtended(true);
        requestLog.setLogCookies(true);
        requestLog.setLogTimeZone("GMT");
        requestLog.setLogServer(true);
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(requestLog);

        ResourceConfig commonServletConfig = new ResourceConfig();
        commonServletConfig.packages(serverFunctionsPackage);
        commonServletConfig.register(JacksonFeature.class);
    	ServletHolder commonServlet = new ServletHolder(new ServletContainer(commonServletConfig));

    	ServletContextHandler servletContext = new ServletContextHandler(server, "/servlet/*");
    	servletContext.setContextPath("/servlet/*");
    	servletContext.addServlet(commonServlet, "/*");
    	
    	WebAppContext webContext = new WebAppContext();
        webContext.setContextPath("/*");
        webContext.setDescriptor(System.getProperty("ews.webbase", serverWebBasePath) + "/WEB-INF/web.xml");
        webContext.setResourceBase(System.getProperty("ews.webbase", serverWebBasePath));
        webContext.addAliasCheck(new AllowSymLinkAliasChecker());
    	
    	ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { webContext, servletContext });
 
        server.setHandler(contexts);
	}
	
	
	protected void startCommunicationServer() throws Exception {
		startServiceSubscriber();
		server.start();
		server.join();
	}
	
	
	public String getLocalWebServiceHost() {
		return serverServiceHost;
	}
	
	
	public Integer getLocalWebSercivePort() {
		return Integer.parseInt(serverServicePort);
	}
	
	
	abstract public String getServiceName();
	
	abstract public ServiceType getServiceType();
	
	abstract public String getServiceClusterManagerHost();
	
	abstract public Integer getServiceClusterManagerPort();
	
	
	private void startServiceSubscriber() {
		if(getServiceType().equals(ServiceType.CLUSTERMANAGER)) {
			log.debug("Service is a " + ServiceType.CLUSTERMANAGER.getValue() + ". Subscriber is not required.");
			return;
		}
		
		// ---------------------
		// - Define a connector with ClusterManager and start a scheduler to periodically subscribe/update 
		// -  this agent to ClusterManager.
		
		try {
			RemoteConnector rc = new RemoteConnector("https", getServiceClusterManagerHost(), getServiceClusterManagerPort());
			
			compilePolicy();
			
			internalScheduler = Executors.newScheduledThreadPool(1);
			internalScheduler.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					try {
						if(canSchedule()) {
							log.debug("Execute polling task...");
							ObjectMapper mapper = new ObjectMapper();
							ServiceDescriptor sd = new ServiceDescriptor(getServiceName(), getServiceType().getValue(), getLocalWebServiceHost(), getLocalWebSercivePort(), new Long(System.currentTimeMillis()));
							String jsonServiceDescriptor = mapper.writeValueAsString(sd);
							
							log.debug(String.format("JSON CONTENT: %s", jsonServiceDescriptor));
							String jsonRegisterServiceResponse = rc.post("/servlet/cm/registerservice", jsonServiceDescriptor);
							
							RemoteCallResult rcr = null;
							
							if(jsonRegisterServiceResponse != null && !jsonRegisterServiceResponse.isEmpty()) {
								try {
									
									rcr = mapper.readValue(jsonRegisterServiceResponse, RemoteCallResult.class);
									if(rcr != null) {
										if(rcr instanceof RemoteCallResultInterface) {
											log.info(String.format("Got result from ClusterManager CODE:='%d' TEXT:='%s'", rcr.getResultCode(), rcr.getResultText()));
										}
										else {
											log.debug(String.format("Invalid result."));
										}
									}
									else {
										log.debug(String.format("Invalid 'null' element."));
									}
								}
								catch (IOException e) {
									log.error(e);
									throw e;
								}
							}
						}
					}
					catch (Exception e) {
						log.error(e);
					}
				}
			}, 100, 200, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			log.error(e);
		}
		
		// ---------------------
	}
	
	
	private boolean canSchedule() {
		if(minutes==null || hours==null || daysOfMonth==null || months==null || daysOfWeek==null) {
			return false;
		}
		
		cal.setTimeInMillis(System.currentTimeMillis());
		
		if(cal.get(Calendar.MILLISECOND) > 200)
			return false;
		
		if(!seconds.contains(cal.get(Calendar.SECOND)))
			return false;
		
		if(!minutes.contains(cal.get(Calendar.MINUTE)))
			return false;
		
		if(!hours.contains(cal.get(Calendar.HOUR_OF_DAY)))
			return false;
		
		if(!daysOfMonth.contains(cal.get(Calendar.DAY_OF_MONTH)))
			return false;
		
		if(!months.contains(cal.get(Calendar.MONTH) + 1))
			return false;
		
		if(!daysOfWeek.contains(cal.get(Calendar.DAY_OF_WEEK) - 1))
			return false;
		
		return true;
	}
	
	
	private void compilePolicy() throws Exception {
		if(seconds == null)
			seconds		= new Vector<Integer>();
		else
			seconds.clear();
		
		if(minutes == null)
			minutes		= new Vector<Integer>();
		else
			minutes.clear();
		
		if(hours == null)
			hours		= new Vector<Integer>();
		else
			hours.clear();
		
		if(daysOfMonth == null)
			daysOfMonth		= new Vector<Integer>();
		else
			daysOfMonth.clear();
		
		if(months == null)
			months		= new Vector<Integer>();
		else
			months.clear();
		
		if(daysOfWeek == null)
			daysOfWeek		= new Vector<Integer>();
		else
			daysOfWeek.clear();
		
		if(cal == null)
			cal = Calendar.getInstance();
		
		String[] policies = schedulerPolicy.split(" ");
		
		if(policies.length != 6) {
			Exception ex = new Exception("Bad policy definition. Must be <minutes> <hours> <daysOfMonth> <months> <daysOfWeek>.");
			log.error(ex);
			throw ex;
		}
		
		
		// ----------------------------------------------------------------------------------------- //
		// -- S E C O N D S ------------------------------------------------------------------------ //
		// ----------------------------------------------------------------------------------------- //
		if(policies[SECONDS_POLICY_IDX].equals("*")) {
			log.debug("Second policy match with '*'");
			for(int i = 0; i < 60; i++) {
				seconds.add(i);
			}
		}
		else if(policies[SECONDS_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Second policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] secondList = policies[SECONDS_POLICY_IDX].split(",");
			for(String m : secondList) {
				try {
					int second = Integer.parseInt(m.trim());
					if(second > 59 || second < 0) {
						Exception ex = new Exception("Bad second policy definition. Second value must be between 0 and 59.");
						log.error(ex);
						throw ex;
					}
					
					if(!seconds.contains(second))
						seconds.add(second);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad second policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[SECONDS_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Second policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[SECONDS_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor) {
					Exception ex = new Exception("Bad second policy definition. In <N>/<M> format <M> must be greater than <N>.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 0; i < 60; i++) {
					if(i % divisor == rest)
						seconds.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad second policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[SECONDS_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[SECONDS_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Second policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[SECONDS_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 0 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 59 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
							seconds.add(i);
					}
				}
				else {
					for(int i=0; i < endRange; i++) {
						seconds.add(i);
					}
					for(int i = startRange + 1; i < 60; i++) {
						seconds.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad second policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		
		// ----------------------------------------------------------------------------------------- //
		// -- M I N U T E S ------------------------------------------------------------------------ //
		// ----------------------------------------------------------------------------------------- //
		if(policies[MINUTES_POLICY_IDX].equals("*")) {
			log.debug("Minute policy match with '*'");
			for(int i = 0; i < 60; i++) {
				minutes.add(i);
			}
		}
		else if(policies[MINUTES_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Minute policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] minuteList = policies[MINUTES_POLICY_IDX].split(",");
			for(String m : minuteList) {
				try {
					int minute = Integer.parseInt(m.trim());
					if(minute > 59 || minute < 0) {
						Exception ex = new Exception("Bad minute policy definition. Minute value must be between 0 and 59.");
						log.error(ex);
						throw ex;
					}
					
					if(!minutes.contains(minute))
						minutes.add(minute);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad minute policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[MINUTES_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Minute policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[MINUTES_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor) {
					Exception ex = new Exception("Bad minute policy definition. In <N>/<M> format <M> must be greater than <N>.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 0; i < 60; i++) {
					if(i % divisor == rest)
						minutes.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad minute policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[MINUTES_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[MINUTES_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Minute policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[MINUTES_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 0 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 59 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
							minutes.add(i);
					}
				}
				else {
					for(int i=0; i < endRange; i++) {
						minutes.add(i);
					}
					for(int i = startRange + 1; i < 60; i++) {
						minutes.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad minute policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		
		
		// ----------------------------------------------------------------------------------------- //
		// -- H O U R S ---------------------------------------------------------------------------- //
		// ----------------------------------------------------------------------------------------- //
		if(policies[HOURS_POLICY_IDX].equals("*")) {
			log.debug("Hours policy match with '*'");
			for(int i = 0; i < 24; i++) {
				hours.add(i);
			}
		}
		else if(policies[HOURS_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Minute policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] hoursList = policies[HOURS_POLICY_IDX].split(",");
			for(String h : hoursList) {
				try {
					int hour = Integer.parseInt(h.trim());
					if(hour >= 24 || hour < 0) {
						Exception ex = new Exception("Bad hour policy definition. Minute value must be between 0 and 23.");
						log.error(ex);
						throw ex;
					}
					
					if(!hours.contains(hour))
						hours.add(hour);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad hour policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[HOURS_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Hour policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[HOURS_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor || divisor == 0) {
					Exception ex = new Exception("Bad hour policy definition. In <N>/<M> format <M> must be greater than <N> and <M> must be greater than 0.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 0; i < 24; i++) {
					if(i % divisor == rest)
						hours.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad hour policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[HOURS_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[HOURS_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Hour policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[HOURS_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 0 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 23 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
						hours.add(i);
					}
				}
				else {
					for(int i=0; i < endRange; i++) {
						hours.add(i);
					}
					for(int i = startRange + 1; i <24; i++) {
						hours.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad hour policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		
		
		// ----------------------------------------------------------------------------------------- //
		// -- D A Y S   O F   M O N T H ------------------------------------------------------------ //
		// ----------------------------------------------------------------------------------------- //
		if(policies[DAYSOFMONTH_POLICY_IDX].equals("*")) {
			log.debug("Days Of Month policy match with '*'");
			for(int i = 1; i < 32; i++) {
				daysOfMonth.add(i);
			}
		}
		else if(policies[DAYSOFMONTH_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Days Of Month policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] domList = policies[DAYSOFMONTH_POLICY_IDX].split(",");
			for(String d : domList) {
				try {
					int day = Integer.parseInt(d.trim());
					if(day > 31 || day < 1) {
						Exception ex = new Exception("Bad Days Of Month policy definition. Minute value must be between 1 and 31.");
						log.error(ex);
						throw ex;
					}
					
					if(!daysOfMonth.contains(day))
						daysOfMonth.add(day);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad Days Of Month policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[DAYSOFMONTH_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Days Of Month policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[DAYSOFMONTH_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor || divisor == 0) {
					Exception ex = new Exception("Bad Days Of Month policy definition. In <N>/<M> format <M> must be greater than <N> and <M> must be greater than 0.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 1; i < 32; i++) {
					if(i % divisor == rest)
						daysOfMonth.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad Days Of Month policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[DAYSOFMONTH_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[DAYSOFMONTH_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Days Of Month policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[DAYSOFMONTH_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 1 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 31 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
						daysOfMonth.add(i);
					}
				}
				else {
					for(int i=1; i < endRange; i++) {
						daysOfMonth.add(i);
					}
					for(int i = startRange + 1; i < 32; i++) {
						daysOfMonth.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad Days Of Month policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad Days Of Month policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		
		
		
		// ----------------------------------------------------------------------------------------- //
		// -- M O N T H S -------------------------------------------------------------------------- //
		// ----------------------------------------------------------------------------------------- //
		if(policies[MONTHS_POLICY_IDX].equals("*")) {
			log.debug("Months policy match with '*'");
			for(int i = 1; i < 13; i++) {
				months.add(i);
			}
		}
		else if(policies[MONTHS_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Months policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] monthsList = policies[MONTHS_POLICY_IDX].split(",");
			for(String m : monthsList) {
				try {
					int month = Integer.parseInt(m.trim());
					if(month >= 13 || month < 1) {
						Exception ex = new Exception("Bad hour policy definition. Minute value must be between 0 and 23.");
						log.error(ex);
						throw ex;
					}
					
					if(!months.contains(month))
						months.add(month);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad hour policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[MONTHS_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Month policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[MONTHS_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor || divisor == 0) {
					Exception ex = new Exception("Bad month policy definition. In <N>/<M> format <M> must be greater than <N> and <M> must be greater than 0.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 1; i < 13; i++) {
					if(i % divisor == rest)
						months.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad month policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[MONTHS_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[MONTHS_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Month policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[MONTHS_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 0 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 23 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
						months.add(i);
					}
				}
				else {
					for(int i=1; i < endRange; i++) {
						months.add(i);
					}
					for(int i = startRange + 1; i < 13; i++) {
						months.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad month policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad months policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		
		
		// ----------------------------------------------------------------------------------------- //
		// -- D A Y S   O F   W E E K -------------------------------------------------------------- //
		// ----------------------------------------------------------------------------------------- //
		if(policies[DAYSOFWEEK_POLICY_IDX].equals("*")) {
			log.debug("Days Of Week policy match with '*'");
			for(int i = 0; i < 7; i++) {
				daysOfWeek.add(i);
			}
		}
		else if(policies[DAYSOFWEEK_POLICY_IDX].matches("[0-9]{1,2}(,[0-9]{1,2})*")) {
			log.debug("Days Of Week policy match with re '[0-9]{1,2}(,[0-9]{1,2})*'");
			String[] dowList = policies[DAYSOFWEEK_POLICY_IDX].split(",");
			for(String d : dowList) {
				try {
					int day = Integer.parseInt(d.trim());
					if(day > 6 || day < 0) {
						Exception ex = new Exception("Bad Days Of Week policy definition. Minute value must be between 1 and 31.");
						log.error(ex);
						throw ex;
					}
					
					if(!daysOfWeek.contains(day))
						daysOfWeek.add(day);
				}
				catch(NumberFormatException e) {
					Exception ex = new Exception("Bad Days Of Week policy definition. Must be N or N,M,... .");
					log.error(ex);
					throw ex;
				}
			}
		}
		else if(policies[DAYSOFWEEK_POLICY_IDX].matches("([0-9]{1,2})?\\/[0-9]{1,2}")) {
			log.debug("Days Of Week policy match with re '[0-9]{1,2}\\/([0-9]{1,2})?'");
			String[] restAndDivisor = policies[DAYSOFWEEK_POLICY_IDX].split("\\/");
			try {
				int rest = restAndDivisor[0].trim().isEmpty() ? 0 : Integer.parseInt(restAndDivisor[0]);
				int divisor = Integer.parseInt(restAndDivisor[1]);
				if(rest >= divisor || divisor == 0) {
					Exception ex = new Exception("Bad Days Of Week policy definition. In <N>/<M> format <M> must be greater than <N> and <M> must be greater than 0.");
					log.error(ex);
					throw ex;
				}
				
				for(int i = 0; i < 7; i++) {
					if(i % divisor == rest)
						daysOfWeek.add(i);
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad Days Of Week policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else if(policies[DAYSOFWEEK_POLICY_IDX].matches("[0-9]{1,2}\\-([0-9]{1,2})?") || policies[DAYSOFWEEK_POLICY_IDX].matches("([0-9]{1,2})?\\-[0-9]{1,2}")) {
			log.debug("Days Of Week policy match with re '[0-9]{1,2}\\-([0-9]{1,2})?' or '([0-9]{1,2})?\\-[0-9]{1,2}'");
			String[] range = policies[DAYSOFWEEK_POLICY_IDX].split("-");
			try {
				int startRange = range[0].trim().isEmpty() ? 1 : Integer.parseInt(range[0]);
				int endRange = (range.length < 2 || range[1].trim().isEmpty()) ? 31 : Integer.parseInt(range[1]);
				
				if(startRange <= endRange) {
					for(int i = startRange; i <= endRange; i++) {
						daysOfWeek.add(i);
					}
				}
				else {
					for(int i=0; i < endRange; i++) {
						daysOfWeek.add(i);
					}
					for(int i = startRange + 1; i < 7; i++) {
						daysOfWeek.add(i);
					}
				}
			}
			catch(NumberFormatException e) {
				Exception ex = new Exception("Bad Days Of Week policy definition. Must be N or N,M,... .");
				log.error(ex);
				throw ex;
			}
		}
		else {
			Exception ex = new Exception("Bad Days Of Week policy format. Must be '*' or <N>[,<N>]* or <N>-[<N>]");
			log.error(ex);
			throw ex;
		}
		log.debug("Schedulation policy compiled successfully.");
	}
	

	private Logger log = null;
	
	private XMLConfiguration config = null;
	private Server server = null;

	private Integer serverPoolMaxThreads = null;
	private String serverServicePort = null;
	private String serverServiceHost = null;
	private String serverFunctionsPackage = null;
	private String serverBasedirPath = null;
	private String serverWebBasePath = null;
	private String serverEtcPath = null;
	private String serverKeyStoreFile = null;
	private String serverKeyStorePassword = null;
	private String serverTrustStoreFile = null;
	private String serverTrustStorePassword = null;
	
	private List<Integer> seconds = null;
	private List<Integer> minutes = null;
	private List<Integer> hours	= null;
	private List<Integer> daysOfMonth = null;
	private List<Integer> months = null;
	private List<Integer> daysOfWeek = null;
	private Calendar cal = null;
	private ScheduledExecutorService internalScheduler = null;
	private final String schedulerPolicy = "0/5 * * * * *";

	private static final String SERVLETS_PACKAGE = "it.ddp.common.servlets";
	protected static final String NONE_STRING = "_#NONE#_";
	
	private static final int SECONDS_POLICY_IDX 		= 0;
	private static final int MINUTES_POLICY_IDX 		= 1;
	private static final int HOURS_POLICY_IDX 			= 2;
	private static final int DAYSOFMONTH_POLICY_IDX 	= 3;
	private static final int MONTHS_POLICY_IDX 			= 4;
	private static final int DAYSOFWEEK_POLICY_IDX 		= 5;
}
