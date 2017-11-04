package it.ddp.services.clustermanager;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.ddp.common.objects.ServiceStatusInfo;
import it.ddp.common.objects.ServiceStatusInfoInterface;
import it.ddp.common.remote.RemoteBaseFunctions;
import it.ddp.common.remote.RemoteConnector;

public abstract class AbstractStrategy implements RemoteBaseFunctions {

	public AbstractStrategy(RemoteConnector rc, String intervallPolicies) throws Exception {
		log = Logger.getLogger(AbstractStrategy.class);
		
		this.connector = rc;
		
		this.intervallPolicies = intervallPolicies;
		compilePolicy();
		
		internalScheduler = Executors.newScheduledThreadPool(1);
		internalScheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					if(canSchedule()) {
						log.debug("Execute polling task...");
						if(isActive)
							executeStrategy();
					}
				}
				catch (Exception e) {
					log.error(e);
				}
			}
		}, 100, 200, TimeUnit.MILLISECONDS);
	}

	
	abstract public void executeStrategy();

	public boolean isActive() {
		return isActive;
	}
	
	public void enable(boolean en) {
		this.isActive = en;
	}
	
	protected RemoteConnector getConnector() {
		return connector;
	}
	
	public void close() throws Exception {
		log.debug(String.format("Stopping ClusterManager '%s'.", "???"));
		internalScheduler.shutdown();
		
		try {
			log.debug("Waiting for agentScheduler termination");
			internalScheduler.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch(InterruptedException e) {
			log.error("Timeout occurred.", e);
		}
		log.debug(String.format("Plugin '%s' closed.", "???"));
	}
	
	
	@Override
	public ServiceStatusInfoInterface getServiceStatus(RemoteConnector r) throws URISyntaxException, IOException,  InterruptedException, TimeoutException, ExecutionException {
		
		String json = null;
		
		try {
			log.info(String.format("Retreive information from '%s'", r.getBaseURI().toString()));
			json = r.post("/servlet/common/info", "");
		}
		catch (URISyntaxException | IOException | InterruptedException | TimeoutException | ExecutionException e) {
			log.error(e);
			json = null;
			throw e;
		}
		
		ServiceStatusInfoInterface s = null;
		
		if(json != null && !json.isEmpty()) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				s = mapper.readValue(json, ServiceStatusInfo.class);
				if(s != null) {
					if(s instanceof ServiceStatusInfoInterface) {
						log.info(String.format("Got information from element '%s' of type '%s'", s.getName(), s.getType()));
					}
					else {
						log.debug(String.format("Invalid element '%s' of type '%s'.", s.getName(), s.getType()));
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
		
		return s;
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
		
		String[] policies = intervallPolicies.split(" ");
		
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
	
	private List<Integer> seconds = null;
	private List<Integer> minutes = null;
	private List<Integer> hours	= null;
	private List<Integer> daysOfMonth = null;
	private List<Integer> months = null;
	private List<Integer> daysOfWeek = null;
	private Calendar cal = null;
	
	private Boolean isActive = false;
	private String intervallPolicies = null;
	private ScheduledExecutorService internalScheduler = null;
	private RemoteConnector connector = null;
	
	private static final int SECONDS_POLICY_IDX 		= 0;
	private static final int MINUTES_POLICY_IDX 		= 1;
	private static final int HOURS_POLICY_IDX 			= 2;
	private static final int DAYSOFMONTH_POLICY_IDX 	= 3;
	private static final int MONTHS_POLICY_IDX 			= 4;
	private static final int DAYSOFWEEK_POLICY_IDX 		= 5;

}
