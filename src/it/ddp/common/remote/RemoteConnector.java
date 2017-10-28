package it.ddp.common.remote;

import java.io.File;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStoreException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;


public class RemoteConnector {
	
	enum RequestType {
		GET,
		POST;
	}
	
	public RemoteConnector(String protocol, String host, Integer port, String keyStore, String keyStorePassword) throws Exception {
		this.protocol = protocol;
		this.host = host;
		this.port = port;
		
		initConnector(keyStore, keyStorePassword);
	}

	
	public RemoteConnector(String protocol, String host, Integer port) throws Exception {
		
		this.protocol = protocol;
		this.host = host;
		this.port = port;
		
		String keyStore = System.getProperty("ews.filestore.file", NONE_STRING);
		if(keyStore==null || keyStore.equals(NONE_STRING)) {
			KeyStoreException e = new KeyStoreException("Missing required parameter 'ews.filestore.file'.");
			log.error(e);
			throw e;
		}
		
		String keyStorePassword = System.getProperty("ews.filestore.password", NONE_STRING);
		if(keyStorePassword==null || keyStorePassword.equals(NONE_STRING)) {
			KeyStoreException e = new KeyStoreException("Missing required parameter 'ews.filestore.password'.");
			log.error(e);
			throw e;
		}
		
		initConnector(keyStore, keyStorePassword);
	}
	
	private void initConnector(String keystore, String keystorepassword) throws Exception {
		log = Logger.getLogger(RemoteConnector.class);
		
		File ksFile = new File(keystore);
		if(ksFile == null || !ksFile.exists() || !ksFile.isFile() || !ksFile.canRead()) {
			KeyStoreException e = new KeyStoreException(String.format("FileStore '%s' does not exists or it is not an existing readable file.", keystore));
			log.error(e);
			throw e;
		}
		
		// SSL Context Factory
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(ksFile.getAbsolutePath());
        sslContextFactory.setKeyStorePassword(keystorepassword);
        sslContextFactory.setKeyManagerPassword(keystorepassword);
        sslContextFactory.setTrustStorePath(ksFile.getAbsolutePath());
        sslContextFactory.setTrustStorePassword(keystorepassword);
        sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
 
        httpClient = new HttpClient(sslContextFactory);
        httpClient.setFollowRedirects(false);
        httpClient.setConnectTimeout(1000);
        httpClient.setAddressResolutionTimeout(500);
        httpClient.setIdleTimeout(1000);

        httpClient.start();
	}
	
	
	private String request(String service, HttpMethod t) throws URISyntaxException, IOException, InterruptedException, TimeoutException, ExecutionException {
		String responseBody = null;
		
		String urlService = service;
		if(urlService == null || urlService.isEmpty()) {
			log.debug("Service not specified. Apply default service.");
			urlService = DEFAULT_URL_SERVICE;
		}
		
		URL req = new URL(protocol, host, port, urlService);
		
		log.debug(String.format("Retrieve information from agent '%s'", req.toURI()));
		
		ContentResponse response = httpClient
				.newRequest(req.toURI())
				.followRedirects(false)
				.method(t)
				.timeout(1000, TimeUnit.MILLISECONDS)
		        .send();
		
		log.debug(String.format("Request sent. '%s'", req.toURI()));
		
		if(response.getStatus() >= HttpStatus.OK_200 && response.getStatus() < HttpStatus.MOVED_PERMANENTLY_301) {
			log.debug("Response status is " + HttpStatus.getMessage(response.getStatus()));
			responseBody = new String(response.getContent());
		}
		else {
			IOException e = new IOException("Unexpected response status: " + response.getStatus());
			log.error(e);
			response.abort(e);
		}
		
		log.debug(responseBody);
		return responseBody;
	}
	
	
	public String get(String service) throws URISyntaxException, IOException, InterruptedException, TimeoutException, ExecutionException  {
		return request(service, HttpMethod.GET);
	}
	
	public String post(String service) throws InterruptedException, TimeoutException, ExecutionException, URISyntaxException, IOException {
		return request(service, HttpMethod.POST);
	}
	
	
	public URI getBaseURI() {
		URI res = null;
		
		try {
			URL req = new URL(protocol, host, port, "");
			res = req.toURI();
		}
		catch(Exception e) {
			res = null;
		}
		
		return res;
	}
	
	
	public void close() throws Exception {
		if(httpClient != null) {
			try {
				log.debug("Stop client...");
				if(httpClient.isStarted())
					httpClient.stop();
				log.debug("Destroy client...");
				httpClient.destroy();
			}
			catch(Exception e) {
				log.error(e);
				throw e;
			}
		}
	}
	
	
	@Override
	public void finalize() throws Throwable {
		super.finalize();
		close();
	}
	
	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("h", "help", false, "Print this help");
		options.addOption("p", "protocol", true, "Connection protocol (http/https)");
		options.addOption("j", "host", true, "Connection host");
		options.addOption("k", "port", true, "Connection port");
		options.addOption("s", "service", true, "Request service");
		options.addOption("f", "keystore", true, "KeyStore file");
		options.addOption("w", "password", true, "KeyStore password");
		options.addOption("m", "method", false, "Request methos (default get)");
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, args);
		HelpFormatter formatter = new HelpFormatter();
		
		/*
		System.out.println("h ->" + cmd.hasOption("h"));
		System.out.println("p ->" + cmd.hasOption("p"));
		System.out.println("j ->" + cmd.hasOption("j"));
		System.out.println("k ->" + cmd.hasOption("k"));
		System.out.println("s ->" + cmd.hasOption("s"));
		System.out.println("f ->" + cmd.hasOption("f"));
		System.out.println("w ->" + cmd.hasOption("w"));
		*/
		
		if(cmd.hasOption("h") || !cmd.hasOption("p") || !cmd.hasOption("j") || !cmd.hasOption("k") || !cmd.hasOption("s") || !cmd.hasOption("f") || !cmd.hasOption("w")) {
			formatter.printHelp(RemoteConnector.class.getName(), options );
			System.exit(0);
		}
		
		String protocol = cmd.getOptionValue("p");
		String host = cmd.getOptionValue("j");
		Integer port = Integer.decode(cmd.getOptionValue("k"));
		String service = cmd.getOptionValue("s");
		String keystore = cmd.getOptionValue("f");
		String password = cmd.getOptionValue("w");
		String method = (cmd.getOptionValue("m") == null) ? "GET" : cmd.getOptionValue("m").toUpperCase().trim();
		
		
		RemoteConnector hc = new RemoteConnector(protocol, host, port, keystore, password);
		
		for(int i=0; i < 1; i++) {
			if(method.equals("GET")) {
				hc.get(service);
			}
			else if(method.equals("POST")) {
				hc.post(service);
			}
			else {
				System.err.printf("Unknown method '%s'\n", method);
			}
		}
		
		hc.close();
	}
	
	private Logger log = null;
	
	private String protocol = null;
	private String host = null;
	private Integer port = null;	

	private HttpClient httpClient = null;
	
	private static final String NONE_STRING = "_#NONE#_";
	private static final String DEFAULT_URL_SERVICE = "/servlet/common/info";

}

