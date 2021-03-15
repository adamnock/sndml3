package sndml.servicenow;

import java.io.PrintWriter;
import java.util.Date;

public class WriterMetrics {

	private String name = null;
	private WriterMetrics parent = null;
	private int inserted = 0;
	private int updated = 0;
	private int deleted = 0;
	private int skipped = 0;
	private Date started = null;
	private Date finished = null;

	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}
	
	public void setParent(WriterMetrics parent) {
		this.parent = parent;
	}
	
	public boolean hasParent() {
		return this.parent != null;
	}
	
	public WriterMetrics getParent() {
		return this.parent;
	}
	
	public synchronized WriterMetrics start() {
		if (parent != null) parent.start();
		if (started == null) started = new Date();
		return this;
	}
	
	public synchronized WriterMetrics finish() {
		finished = new Date();
		if (parent != null) parent.finish();
		return this;
	}
	
	public DateTime getStarted() {
		return started == null ? null : new DateTime(started);
	}
	
	public DateTime getFinished() {
		return finished == null ? null : new DateTime(finished);
	}
	
	public double getElapsedSec() {
		if (started == null || finished == null)
			return 0;
		else {
			double result = ((finished.getTime() - started.getTime()) / 1000.0);			
			return result;
		}
	}
		
	public int getProcessed() {
		return getInserted() + getUpdated() + getDeleted() + getSkipped();
	}
	
	public int getInserted() {
		return this.inserted;
	}
	
	public int getUpdated() {
		return this.updated;
	}
	
	public int getDeleted() {
		return this.deleted;
	}
	
	public int getSkipped() {
		return this.skipped;
	}
	
	public void incrementInserted() {
		addInserted(1);
	}
	
	public void incrementUpdated() {
		addUpdated(1);
	}
	
	public void incrementDeleted() {
		addDeleted(1);
	}
	
	public void incrementSkipped() {
		addSkipped(1);
	}

	public synchronized void addInserted(int count) {
		inserted += count;
		if (parent != null) parent.addInserted(count);
	}
	
	public synchronized void addUpdated(int count) {
		updated += count;
		if (parent != null) parent.addUpdated(count);
	}
	
	public synchronized void addDeleted(int count) {
		deleted += count;
		if (parent != null) parent.addDeleted(count);
	}
	
	public synchronized void addSkipped(int count) {
		skipped += count;
		if (parent != null) parent.addSkipped(count);
	}
	
	public synchronized void add(WriterMetrics stats) {
		assert stats != null;
		assert stats.started != null;
		assert stats.finished != null;
		if (started == null || started.getTime() > stats.started.getTime()) started = stats.started;
		if (finished == null || finished.getTime() < stats.finished.getTime()) finished = stats.finished;
		inserted += stats.inserted;
		updated += stats.updated;
		deleted += stats.deleted;
		skipped += stats.skipped;
	}
		
	public void write(PrintWriter writer) {
		String prefix = (name == null ? "" : name + ".");
		writer.println(prefix + "start="     + getStarted());
		writer.println(prefix + "finish="    + getFinished());
		writer.println(prefix + "elapsed="   + String.format("%.1f", getElapsedSec()));
		writer.println(prefix + "inserted="  + String.valueOf(getInserted()));
		writer.println(prefix + "updated="   + String.valueOf(getUpdated()));
		writer.println(prefix + "deleted="   + String.valueOf(getDeleted()));
		writer.println(prefix + "skipped="   + String.valueOf(getSkipped()));
		writer.println(prefix + "processed=" + String.valueOf(getProcessed()));		
	}
	
}
