package de.uniluebeck.itm.rsc.drivers.core.mockdevice;

import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractOperation;
import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractProgressManager;


/**
 * An abstract operation that provides a progress system.
 * 
 * @author Malte Legenhausen
 *
 * @param <T> The return type of the operation.
 */
public abstract class AbstractMockOperation<T> extends AbstractOperation<T> {

	/**
	 * Default amout of sleep time between each iteration.
	 */
	public static final int DEFAULT_SLEEP = 100;
	
	/**
	 * Default amout of iteration steps.
	 */
	public static final int DEFAULT_STEPS = 10;
	
	/**
	 * The sleep time between each iteration.
	 */
	private final int sleep;
	
	/**
	 * The amount iteration steps.
	 */
	private final int steps;
	
	/**
	 * The size of each step.
	 */
	private final float stepSize;
	
	/**
	 * Constructor.
	 * Sets sleep = 100 and steps = 10 as default.
	 */
	public AbstractMockOperation() {
		this(DEFAULT_SLEEP, DEFAULT_STEPS);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param sleep The amount of sleep time between each iteration.
	 * @param steps The amount of iterations.
	 */
	public AbstractMockOperation(final int sleep, final int steps) {
		this.sleep = sleep;
		this.steps = steps;
		this.stepSize = 1.0f / steps;
	}
	
	@Override
	public T execute(final AbstractProgressManager progressManager) throws Exception {
		prepare();
		for(int i = 1; i <= steps && !isCanceled(); ++i) {
			Thread.sleep(sleep);
			iteration(i);
			progressManager.worked(stepSize);
		}
		return returnResult();
	}

	/**
	 * Method is called before the progress starts.
	 */
	protected void prepare() {
		
	}
	
	/**
	 * Method is called on each iteration step.
	 * 
	 * @param step The current iteration step. A value between 0 and <code>steps</code>.
	 */
	protected void iteration(final int step) {
		
	}
	
	/**
	 * Method is called when the execution has finished and wants to return a result.
	 * 
	 * @return The operation result.
	 */
	protected T returnResult() {
		return null;
	}
}
