package sndml.daemon;

import java.io.IOException;
import java.sql.SQLException;

import sndml.datamart.CompositeProgressLogger;
import sndml.datamart.ConnectionProfile;
import sndml.datamart.JobConfig;
import sndml.datamart.JobRunner;
import sndml.datamart.Log4jProgressLogger;
import sndml.datamart.ResourceException;
import sndml.servicenow.*;

public class DaemonJobRunner extends JobRunner implements Runnable {
	
	final ConnectionProfile profile;
	final Key runKey;
	final String number;
	final DaemonStatusLogger statusLogger;
//	DaemonProgressLogger appLogger;
		
	public DaemonJobRunner(ConnectionProfile profile, JobConfig config) {
		super(profile.getSession(), profile.getDatabase(), config);
		this.profile = profile;
		this.runKey = config.getSysId();
		this.number = config.getNumber();
		assert runKey != null;
		assert runKey.isGUID();
		assert number != null;
		assert number.length() > 0;
		this.table = session.table(config.getSource());
		this.statusLogger = new DaemonStatusLogger(profile, session);
		
	}

	@Override
	protected void createJobProgressLogger(TableReader reader) {
		Log4jProgressLogger textLogger = 
			new Log4jProgressLogger(reader.getClass(), action, jobMetrics);		
		DaemonProgressLogger appLogger =
			new DaemonProgressLogger(profile, session, jobMetrics, number, runKey);
		ProgressLogger compositeLogger = new CompositeProgressLogger(textLogger, appLogger);
		reader.setMetrics(jobMetrics);
		reader.setProgressLogger(compositeLogger);
		assert appLogger.getMetrics() != null;
	}
		
	@Override
	public void run() {
		this.call();
	}

	@Override
	protected void close() throws ResourceException {
		// Close the database connection
		try {
			db.close();
		} catch (SQLException e) {
			throw new ResourceException(e);
		}
	}
	
	@Override
	public Metrics call() {
		try {
			assert session != null;
			assert db != null;
			assert config.getNumber() != null;
			Thread.currentThread().setName(config.number);			
			Metrics metrics = super.call();
			Daemon.rescan();
			return metrics;
		} catch (SQLException | IOException | InterruptedException e) {
			Log.setJobContext(this.getName());
			logger.error(Log.FINISH, e.toString(), e);
			statusLogger.logError(runKey, e);
			return null;
		} catch (Error e) {
			logger.error(Log.FINISH, e.toString(), e);			
			logger.error(Log.FINISH, "Critical error detected. Halting JVM.");
			Runtime.getRuntime().halt(-1);
			return null;
		}
	}
		
}
