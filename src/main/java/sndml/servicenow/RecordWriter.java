package sndml.servicenow;

import java.io.IOException;
import java.sql.SQLException;

/**
 * A class which knows how to process records retrieved from ServiceNow.
 * 
 * Although this is normally instantiated as a {@link sndml.datamart.DatabaseTableWriter}.
 * there are several other subclasses.
 *
 */
public abstract class RecordWriter {

	protected final WriterMetrics writerMetrics = new WriterMetrics();

	public RecordWriter() {
	}

//	public void setProgressLogger(ProgressLogger progressLogger) {
//		assert progressLogger != null;
//		this.progressLogger = progressLogger;
//	}
	
	public abstract void processRecords(RecordList recs, ProgressLogger progressLogger) 
		throws IOException, SQLException;	

	public RecordWriter open() throws IOException, SQLException {
		writerMetrics.start();
		return this;
	}
	
	public void close() throws IOException, SQLException {
		writerMetrics.finish();
	}

	public void setParentMetrics(WriterMetrics parentMetrics) {
		writerMetrics.setParent(parentMetrics);
	}
		
	public WriterMetrics getWriterMetrics() {
		if (writerMetrics.getFinished() == null) writerMetrics.finish();
		return writerMetrics;
	}
	
}
