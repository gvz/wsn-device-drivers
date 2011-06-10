package de.uniluebeck.itm.wsn.drivers.core.async.thread;

import static org.junit.Assert.fail;

import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.uniluebeck.itm.wsn.drivers.core.async.AsyncAdapter;
import de.uniluebeck.itm.wsn.drivers.core.async.AsyncCallback;
import de.uniluebeck.itm.wsn.drivers.core.async.OperationFuture;
import de.uniluebeck.itm.wsn.drivers.core.async.OperationQueue;
import de.uniluebeck.itm.wsn.drivers.core.operation.AbstractOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.Operation;
import de.uniluebeck.itm.wsn.drivers.core.operation.ProgressManager;

public class PausableExecutorOperationQueueTest {

	private OperationQueue queue;
	
	@Before
	public void setUp() {
		queue = new PausableExecutorOperationQueue();
	}
	
	@Test
	public void testAddOperation() throws InterruptedException, ExecutionException {
		Operation<Boolean> operation = new AbstractOperation<Boolean>() {
			@Override
			public Boolean execute(ProgressManager progressManager) throws Exception {
				return true;
			}
		};
		AsyncCallback<Boolean> callback = new AsyncAdapter<Boolean>() {

			@Override
			public void onCancel() {
				fail("No cancel was triggered");
			}

			@Override
			public void onFailure(Throwable throwable) {
				fail("No failure was expected");
			}
		};
		OperationFuture<Boolean> handle = queue.addOperation(operation, 1000, callback);
		if (!handle.get()) {
			fail("Execution failed");
		}
	}

	@Test
	public void testGetOperations() {
		Operation<Boolean> operation = new AbstractOperation<Boolean>() {
			@Override
			public Boolean execute(ProgressManager progressManager) throws Exception {
				Thread.sleep(100);
				return true;
			}
		};
		queue.addOperation(operation, 1000, new AsyncAdapter<Boolean>());
		Assert.assertTrue(!queue.getOperations().isEmpty());
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Assert.assertTrue(queue.getOperations().isEmpty());
	}
	
	@Test(expected=ExecutionException.class)
	public void testOperationHandleException() throws ExecutionException, InterruptedException {
		Operation<Boolean> operation = new AbstractOperation<Boolean>() {
			@Override
			public Boolean execute(ProgressManager progressManager) throws Exception {
				Thread.sleep(500);
				throw new NullPointerException();
			}
		};
		final OperationFuture<Boolean> handle = queue.addOperation(operation, 1000, new AsyncAdapter<Boolean>());
		handle.get();
	}
}
