package sndml.datamart;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sndml.servicenow.*;

public abstract class JobRunner implements Callable<WriterMetrics> {

	protected Session session;
	protected Database db;
	protected Table table;
	protected String sqlTableName;
	protected String tableLoaderName;
	protected JobConfig config;
	protected WriterMetrics metrics;
	protected AppRunLogger appRunLogger;		
	protected final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private void setLogContext() {
		Log.setContext(table, tableLoaderName);		
	}
		
	String getName() {
		return this.table.getName();
	}
	
	JobConfig getConfig() {
		return this.config;
	}

	WriterMetrics getMetrics() {
		return metrics;
	}

	public WriterMetrics call() throws SQLException, IOException, InterruptedException {
		assert config != null;
		assert sqlTableName != null;
		assert sqlTableName.length() > 0;
		Action action = config.getAction();
		assert action != null;
		DateTimeRange createdRange = config.getCreated();
		ProgressLogger progressLogger;
		
		int pageSize = config.getPageSize() == null ? 0 : config.getPageSize().intValue();
		FieldNames fieldNames = null;
		if (config.getIncludeColumns() != null)
			fieldNames = config.getIncludeColumns();
		else if (config.getExcludeColumns() != null)
			fieldNames = table.getSchema().getFieldsMinus(config.getExcludeColumns());
		
		this.setLogContext();
		logger.debug(Log.INIT, 
			String.format("call table=%s action=%s", table.getName(), action.toString()));
		if (config.getSqlBefore() != null) {
			db.executeStatement(config.getSqlBefore());
		}		
		this.setLogContext();

		if (Action.CREATE.equals(action)) {
			if (config.getDropTable()) db.dropTable(sqlTableName, true);
			db.createMissingTable(table, sqlTableName);
		}
		else if (Action.DROPTABLE.equals(action) ) {
			db.dropTable(sqlTableName, true);
		}
		else if (Action.EXECUTE.equals(action) ) {
			db.executeStatement(config.getSql());
		}
		else if (Action.PRUNE.equals(action)) {
			progressLogger = new CompositeProgressLogger(DatabaseDeleteWriter.class, appRunLogger); 
			DatabaseDeleteWriter deleteWriter = 
				new DatabaseDeleteWriter(db, table, sqlTableName, progressLogger);
			deleteWriter.setParentMetrics(metrics);
			deleteWriter.open();
			Table audit = session.table("sys_audit_delete");
			EncodedQuery auditQuery = new EncodedQuery(audit);
			auditQuery.addQuery("tablename", EncodedQuery.EQUALS, table.getName());
			RestTableReader auditReader = new RestTableReader(audit);
			auditReader.enableStats(true);
			auditReader.orderByKeys(true);
			auditReader.setQuery(auditQuery);			
			DateTime since = config.getSince();
			auditReader.setCreatedRange(new DateTimeRange(since, null));
			auditReader.setMaxRows(config.getMaxRows());
			auditReader.setWriter(deleteWriter);
			auditReader.initialize();
			this.setLogContext();
			logger.info(Log.INIT, String.format("begin delete %s (%d rows)", 
				tableLoaderName, auditReader.getReaderMetrics().getExpected()));
			auditReader.call();
			deleteWriter.close();
		}
		else if (Action.SYNC.equals(action)) {
			runSync();
			/*
			db.createMissingTable(table, sqlTableName);
			Interval partitionInterval = config.getPartitionInterval();
			TableReader reader;
			if (partitionInterval == null) {
				progressLogger = new CompositeProgressLogger(Synchronizer.class, appRunLogger);
				Synchronizer syncReader = 
					new Synchronizer(table, db, sqlTableName, metrics, progressLogger);
				syncReader.setFields(fieldNames);
				syncReader.setPageSize(pageSize);
				syncReader.initialize(createdRange);
				reader = syncReader;
			}
			else {
				SynchronizerFactory factory = 
					new SynchronizerFactory(table, db, sqlTableName, this.metrics, createdRange, appRunLogger);
				factory.setFields(fieldNames);
				factory.setPageSize(pageSize);
				DatePartitionedTableReader multiReader =
					new DatePartitionedTableReader(factory, partitionInterval, config.getThreads());
				factory.setParent(multiReader);				
				multiReader.initialize();
				reader = multiReader;
				this.setLogContext();
				logger.info(Log.INIT, "partition=" + multiReader.getPartition());
			}
			logger.info(Log.INIT, String.format("begin sync %s (%d rows)", 
					tableLoaderName, reader.getReaderMetrics().getExpected()));
			reader.call();
			*/
		}
		else /* Update or Insert */ {
			if (config.getAutoCreate()) db.createMissingTable(table, sqlTableName);
			if (config.getTruncate()) db.truncateTable(sqlTableName);
			DatabaseTableWriter writer;
			if (Action.UPDATE.equals(action)) {
				progressLogger = new CompositeProgressLogger(DatabaseUpdateWriter.class, appRunLogger);
				writer = new DatabaseUpdateWriter(db, table, sqlTableName, progressLogger);
			}
			else if (Action.INSERT.equals(action)) {
				progressLogger = new CompositeProgressLogger(DatabaseInsertWriter.class, appRunLogger);
				writer = new DatabaseInsertWriter(db, table, sqlTableName, progressLogger);
			}
			else
				throw new AssertionError("action=" + action);
			writer.setParentMetrics(metrics);
			writer.open();
			
			Interval partitionInterval = config.getPartitionInterval();
			TableReaderFactory factory;
			DateTime since = config.getSince();	
			logger.debug(Log.INIT, "since=" + config.sinceExpr + "=" + since);
			if (since != null) {
				factory = new KeySetTableReaderFactory(table, writer);
				factory.setUpdated(since);				
			}
			else {
				factory = new RestTableReaderFactory(table, writer);
			}
			factory.setReaderName(tableLoaderName);
			factory.setFilter(new EncodedQuery(table, config.getFilter()));
			factory.setCreated(createdRange);
			factory.setFields(fieldNames);
			factory.setPageSize(pageSize);
			TableReader reader;
			if (partitionInterval == null) {
				reader = factory.createReader();
				reader.setMaxRows(config.getMaxRows());
				Log.setContext(table, tableLoaderName);
				if (since != null) logger.info(Log.INIT, "getKeys " + reader.getQuery().toString());
				reader.initialize();
			}
			else {
				Integer threads = config.getThreads();
				DatePartitionedTableReader multiReader = 
						new DatePartitionedTableReader(factory, partitionInterval, threads);
				reader = multiReader;
				factory.setParent(multiReader);
				multiReader.initialize();
				Log.setContext(table, tableLoaderName);
				logger.info(Log.INIT, "partition=" + multiReader.getPartition());
			}
			logger.info(Log.INIT, String.format("begin load %s (%d rows)", 
					tableLoaderName, reader.getReaderMetrics().getExpected()));
			reader.call();
			writer.close();			
		}
		
		int processed = metrics.getProcessed();
		logger.info(Log.FINISH, String.format("end load %s (%d rows)", tableLoaderName, processed));
		Integer minRows = config.getMinRows();
		if (minRows != null && processed < minRows)
			throw new TooFewRowsException(table, minRows, processed);
		if (config.getSqlAfter() != null) {
			db.executeStatement(config.getSqlAfter());
		}
		return this.metrics;
	}
	
	void runSync() throws SQLException, IOException, InterruptedException {
		assert config != null;
		DateTimeRange createdRange = config.getCreated();
		ProgressLogger progressLogger;
		if (config.getAutoCreate()) 
			db.createMissingTable(table, sqlTableName);
		Interval partitionInterval = config.getPartitionInterval();
		TableReader reader;
		if (partitionInterval == null) {
			progressLogger = new CompositeProgressLogger(Synchronizer.class, appRunLogger);
			Synchronizer syncReader = 
				new Synchronizer(table, db, sqlTableName, metrics, progressLogger);
			syncReader.setFields(config.getColumns(table));
			syncReader.setPageSize(config.getPageSize());
			syncReader.initialize(createdRange);
			reader = syncReader;
		}
		else {
			SynchronizerFactory factory = 
				new SynchronizerFactory(table, db, sqlTableName, this.metrics, createdRange, appRunLogger);
			factory.setFields(config.getColumns(table));
			factory.setPageSize(config.getPageSize());
			DatePartitionedTableReader multiReader =
				new DatePartitionedTableReader(factory, partitionInterval, config.getThreads());
			factory.setParent(multiReader);				
			multiReader.initialize();
			reader = multiReader;
			this.setLogContext();
			logger.info(Log.INIT, "partition=" + multiReader.getPartition());
		}
		logger.info(Log.INIT, String.format("begin sync %s (%d rows)", 
				tableLoaderName, reader.getReaderMetrics().getExpected()));
		reader.call();
	}

}
