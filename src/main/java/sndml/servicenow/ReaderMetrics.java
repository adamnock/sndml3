package sndml.servicenow;

@Deprecated
public final class ReaderMetrics {

	private int count = 0;
	private Integer expected = null;
	private ReaderMetrics parent = null;
	
	public void setParent(ReaderMetrics parent) {
		this.parent = parent;
	}
	
	public boolean hasParent() {
		return this.parent != null;		
	}
	
	public ReaderMetrics getParent() {
		return this.parent;
	}

	public void increment() {
		increment(1);
	}

	public synchronized void increment(int n) {
		count += n;
		if (parent != null) parent.increment(n);
	}
	
	public synchronized int getCount() {
		return count;
	}
	
	/**
	 * Set the number of rows that the reader is expected to return
	 */
	public synchronized void setExpected(Integer value) {
		expected = value;
	}
	
	public synchronized boolean hasExpected() {
		return expected != null;
	}
	
	/**
	 * Return the number of rows that the reader is expected to return.
	 */
	public synchronized Integer getExpected() {
		return expected;
	}
	
	@Deprecated
	public synchronized String getProgress() {
		if (hasExpected())
			return String.format("%d / %d",  getCount(), getExpected());
		else
			return String.format("%d",  getCount());
	}
	
}
