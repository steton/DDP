package it.ddp.services.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

import it.ddp.main.Starter;

public abstract class AbstractService {
	
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
		server.start();
		server.join();
	}
	
	
	public String getLocalWebServiceHost() {
		return serverServiceHost;
	}
	
	
	public Integer getLocalWebSercivePort() {
		return Integer.parseInt(serverServicePort);
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

	private static final String SERVLETS_PACKAGE = "it.ddp.common.servlets";
	protected static final String NONE_STRING = "_#NONE#_";
}
