package it.ddp.main;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.interpol.ConfigurationInterpolator;
import org.apache.commons.configuration2.interpol.Lookup;
import org.apache.log4j.Logger;

import it.ddp.applications.ClusterManager;
import it.ddp.applications.Consumer;
import it.ddp.applications.Producer;
import it.ddp.applications.ServiceAgent;

public class Starter {
	
	public Starter(String[] args) throws Exception {
		
		log = Logger.getLogger(Starter.class);
		log.debug("Starter activated...");
				
		Options options = new Options();
		options.addOption("h", "help", false, "Print this help");
		options.addOption("x", "xml", true, "XML config file");
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, args);
		HelpFormatter formatter = new HelpFormatter();
		
		if(cmd.hasOption("h") || !cmd.hasOption("x")) {
			formatter.printHelp(Producer.class.getName(), options );
			System.exit(0);
		}
		
		String xmlConfig = cmd.getOptionValue("x");
		File xmlConfigFile = new File(xmlConfig);
		if(xmlConfigFile == null || !xmlConfigFile.exists() || !xmlConfigFile.isFile() || !xmlConfigFile.canRead()) {
			formatter.printHelp(Producer.class.getName(), options);
			System.err.printf("File %s is not a valid readable properties file.\n", xmlConfig);
			System.exit(0);
		}
		
		log.debug(String.format("Load configuration from file '%s'", xmlConfigFile.getAbsolutePath()));
		
		Map<String, Lookup> lookups = new HashMap<String, Lookup>(ConfigurationInterpolator.getDefaultPrefixLookups());
		
		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
		    .configure(params.xml()
		    		.setFileName(xmlConfigFile.getAbsolutePath())
		    		.setValidating(false)
		    		.setPrefixLookups(lookups));

		XMLConfiguration config = builder.getConfiguration();
		
		log.debug(String.format("Parsing required parameters"));
		
		String applicationType = config.getString("application[@type]", NONE_STRING);
		if(applicationType==null || applicationType.equals(NONE_STRING)) {
			ConfigurationException e = new ConfigurationException("Missing required parameter 'type' in tag 'application'.");
			log.error(e);
			throw e;
		}
		
		lookups.put("application[@type]", new Lookup() {	
			@Override
			public Object lookup(String arg0) {
				return applicationType;
			}
		});
		
		String workDir = config.getString("application.workdir[@value]", NONE_STRING);
		if(workDir==null || workDir.equals(NONE_STRING)) {
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
		lookups.put("application.workdir[@value]", new Lookup() {	
			@Override
			public Object lookup(String arg0) {
				return workDir;
			}
		});
		
		File libDirFile = new File(workDirFile.getAbsolutePath() + File.separator + "lib");
		if(libDirFile == null || !libDirFile.exists() || !libDirFile.isDirectory() || !libDirFile.canRead()) {
			ConfigurationException e = new ConfigurationException(String.format("Directory '%s' does not exists or it is not an existing readable directory.", workDirFile.getAbsolutePath() + File.separator + "lib"));
			log.error(e);
			throw e;
		}
		
		File etcDirFile = new File(workDirFile.getAbsolutePath() + File.separator + "etc");
		if(etcDirFile == null || !etcDirFile.exists() || !etcDirFile.isDirectory() || !etcDirFile.canRead()) {
			ConfigurationException e = new ConfigurationException(String.format("Directory '%s' does not exists or it is not an existing readable directory.", workDirFile.getAbsolutePath() + File.separator + "etc"));
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
		
		log.debug(String.format("Parameter application[type] := %s", applicationType));
		log.debug(String.format("Parameter webserver[host] := %s", webServerHost));
		log.debug(String.format("Parameter webserver[port] := %s", webServerPort));
		log.debug(String.format("Parameter application.workdir[value] := %s", workDir));
		
		File[] jarList = libDirFile.listFiles(new FileFilter() {
			@Override
			public boolean accept(File arg0) {
				return arg0.isFile() && arg0.canRead() && arg0.getAbsolutePath().toUpperCase().endsWith("JAR");
			}
		});
		
		for(File f : jarList) {
			addURL(f.toURI().toURL());
		}
        
        addURL(etcDirFile.toURI().toURL());
        
        applicationClassMap = new HashMap<>(); 
        applicationClassMap.put(ClusterManager.TYPE, ClusterManager.class);
        applicationClassMap.put(Consumer.TYPE, Consumer.class);
        applicationClassMap.put(Producer.TYPE, Producer.class);
        applicationClassMap.put(ServiceAgent.TYPE, ServiceAgent.class);
        
        if(!applicationClassMap.containsKey(applicationType)) {
        	ClassNotFoundException e = new ClassNotFoundException(String.format("Application type '%s' is not configurated.", applicationType));
        	log.error(e);
        	throw e;
        }
        
        Class<?> agent = applicationClassMap.get(applicationType);
        try {
			agent.getConstructor(File.class).newInstance(xmlConfigFile);
		}
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException| NoSuchMethodException | SecurityException e) {
			log.error(e);
			throw e;
		}
	}
	
	/*
	private static Class<?>[] getClasses(String packageName) throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes.toArray(new Class[classes.size()]);
    }
	
	private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
            	Class<?> cl = Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
            	if(cl.getCanonicalName() != null)
            		classes.add(cl);
            }
        }
        return classes;
    }
    */
	
	private void addURL(URL u) throws IOException {
        URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;
        try {
            Method method = sysclass.getDeclaredMethod("addURL", parameters);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{ u }); 
        }
        catch (Throwable t) {
            log.error(t);
            throw new IOException("Error, could not add URL to system classloader");
        }        
    }
	
	
	public static void main(String[] args) throws Exception {
		new Starter(args);
	}
	
	private Logger log = null;
	
	private Map<String, Class<?>> applicationClassMap = null;
	
	private static final Class<?>[] parameters = new Class[]{URL.class};
	public static final String NONE_STRING = "_#NONE#_";
	public static final String APPLICATION_PACKAGE_NAME = "it.ddp.applications";
}
