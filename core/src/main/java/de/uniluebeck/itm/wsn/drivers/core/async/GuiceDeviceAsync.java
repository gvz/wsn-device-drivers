package de.uniluebeck.itm.wsn.drivers.core.async;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.google.inject.Injector;

import de.uniluebeck.itm.wsn.drivers.core.ChipType;
import de.uniluebeck.itm.wsn.drivers.core.Connection;
import de.uniluebeck.itm.wsn.drivers.core.DataAvailableListener;
import de.uniluebeck.itm.wsn.drivers.core.MacAddress;
import de.uniluebeck.itm.wsn.drivers.core.State;
import de.uniluebeck.itm.wsn.drivers.core.event.StateChangedEvent;
import de.uniluebeck.itm.wsn.drivers.core.io.SendOutputStreamWrapper;
import de.uniluebeck.itm.wsn.drivers.core.operation.EraseFlashOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.GetChipTypeOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.Operation;
import de.uniluebeck.itm.wsn.drivers.core.operation.ProgramOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.ReadFlashOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.ReadMacAddressOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.ResetOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.SendOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.WriteFlashOperation;
import de.uniluebeck.itm.wsn.drivers.core.operation.WriteMacAddressOperation;


/**
 * Facade for calling operation async on the device.
 * 
 * @author Malte Legenhausen
 */
public class GuiceDeviceAsync implements DeviceAsync {
	/**
	 * Logger for this class.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(GuiceDeviceAsync.class);

	/**
	 * Message for the exception that is thrown when a negative timeout was given.
	 */
	private static final String NEGATIVE_TIMEOUT_MESSAGE = "Negative timeout is not allowed.";
	
	/**
	 * Message for the exception that is thrown when a negative address was given.
	 */
	private static final String NEGATIVE_ADDRESS_MESSAGE = "Negative address is not allowed.";
	
	/**
	 * Message for the exception that is thrown when a negative length was given.
	 */
	private static final String NEGATIVE_LENGTH_MESSAGE = "Negative length is not allowed.";
	
	/**
	 * Queue that schedules all <code>Operation</code> instances.
	 */
	private final OperationQueue queue;

	private PipedInputStream inputStreamPipedInputStream = new PipedInputStream();

	private PipedOutputStream inputStreamPipedOutputStream = new PipedOutputStream();

	private volatile boolean deviceInputStreamAvailableForReading = true;

	private final Lock deviceInputStreamLock = new ReentrantLock();

	private final Condition deviceInputStreamDataAvailable = deviceInputStreamLock.newCondition();

	private Future<?> deviceInputStreamToPipeCopyWorkerFuture;
	
	private final Injector injector;
	
	private final Connection connection;
	
	private final ScheduledExecutorService executorService;

	private class DeviceInputStreamToPipeCopyWorker implements Runnable {
		
		private static final int DATA_AVAILABLE_TIMEOUT = 50;
		
		private volatile boolean shutdown = false;

		@Override
		public void run() {
			try {
				final InputStream inputStream = connection.getInputStream();
				while (!shutdown) {
					deviceInputStreamLock.lock();
					try {
						deviceInputStreamDataAvailable.await(DATA_AVAILABLE_TIMEOUT, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						LOG.error("" + e, e);
					} finally {
						deviceInputStreamLock.unlock();
					}

					if (connection.isConnected() && deviceInputStreamAvailableForReading) {
						copyAvailableBytes(inputStream, inputStreamPipedOutputStream);
					}
				}
			} catch (IOException e) {
				LOG.error("IOException while reading from device InputStream: " + e, e);
			}
		}

		private void copyAvailableBytes(final InputStream inputStream, final OutputStream outputStream)
				throws IOException {

			final int bytesAvailable = inputStream.available();

			if (bytesAvailable > 0) {

				byte[] buffer = new byte[bytesAvailable];
				final int read = inputStream.read(buffer);

				outputStream.write(buffer, 0, read);
			}
		}
		
		public void shutdown() {
			shutdown = true;
		}
	}
	
	private final DeviceInputStreamToPipeCopyWorker deviceInputStreamToPipeCopyWorker =
			new DeviceInputStreamToPipeCopyWorker();

	/**
	 * Constructor.
	 *
	 * @param queue  The <code>OperationQueue</code> that schedules all operations.
	 * @param device The <code>Device</code> that provides all operations that can be executed.
	 */
	@Inject
	public GuiceDeviceAsync(Injector injector) {
		this.injector = injector;
		connection = injector.getInstance(Connection.class);
		executorService = injector.getInstance(ScheduledExecutorService.class);

		this.queue = injector.getInstance(OperationQueue.class);

		try {
			this.inputStreamPipedInputStream.connect(inputStreamPipedOutputStream);
		} catch (IOException e) {
			LOG.error("" + e, e);
			throw new RuntimeException(e);
		}

		queue.addListener(new OperationQueueAdapter<Object>() {
			@Override
			public void afterStateChanged(final StateChangedEvent<Object> event) {
				deviceInputStreamAvailableForReading = !isOperationRunning();
			}
		});

		connection.addListener(new DataAvailableListener() {
			@Override
			public void dataAvailable(final Connection connection) {
				deviceInputStreamLock.lock();
				try {
					deviceInputStreamDataAvailable.signal();
				} finally {
					deviceInputStreamLock.unlock();
				}
			}
		});
		deviceInputStreamToPipeCopyWorkerFuture = executorService.submit(deviceInputStreamToPipeCopyWorker);
	}

	@Override
	public OperationFuture<ChipType> getChipType(long timeout, AsyncCallback<ChipType> callback) {
		LOG.trace("Reading Chip Type (Timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		GetChipTypeOperation operation = injector.getInstance(GetChipTypeOperation.class);
		checkNotNullOperation(operation, "The Operation getChipType is not available");
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> eraseFlash(long timeout, AsyncCallback<Void> callback) {
		LOG.trace("Erase flash (Timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		EraseFlashOperation operation = injector.getInstance(EraseFlashOperation.class);
		checkNotNullOperation(operation, "The Operation eraseFlash is not avialable");
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> program(byte[] data, long timeout, AsyncCallback<Void> callback) {
		LOG.trace("Program device (timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		ProgramOperation operation = injector.getInstance(ProgramOperation.class);
		checkNotNullOperation(operation, "The Operation program is not available");
		operation.setBinaryImage(data);
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<byte[]> readFlash(int address, int length, long timeout, AsyncCallback<byte[]> callback) {
		LOG.trace("Read flash (address: " + address + ", length: " + length + ", timeout: " + timeout + "ms)");
		checkArgument(address >= 0, NEGATIVE_LENGTH_MESSAGE);
		checkArgument(length >= 0, NEGATIVE_ADDRESS_MESSAGE);
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		ReadFlashOperation operation = injector.getInstance(ReadFlashOperation.class);
		checkNotNullOperation(operation, "The Operation readFlash is not available");
		operation.setAddress(address, length);
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<MacAddress> readMac(long timeout, AsyncCallback<MacAddress> callback) {
		LOG.trace("Read mac (timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		ReadMacAddressOperation operation = injector.getInstance(ReadMacAddressOperation.class);
		checkNotNullOperation(operation, "The Operation readMac is not available");
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> reset(long timeout, AsyncCallback<Void> callback) {
		LOG.trace("Reset device (timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		ResetOperation operation = injector.getInstance(ResetOperation.class);
		checkNotNullOperation(operation, "The Operation reset is not available");
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> send(byte[] message, long timeout, AsyncCallback<Void> callback) {
		LOG.trace("Send packet to device (timeout: " + timeout + "ms)");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		SendOperation operation = injector.getInstance(SendOperation.class);
		checkNotNullOperation(operation, "The Operation send is not available");
		operation.setMessage(message);
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> writeFlash(int address,
											byte[] data,
											int length,
											long timeout,
											AsyncCallback<Void> callback) {
		LOG.trace("Write flash (address: " + address + ", length: " + length + ", timeout: " + timeout + "ms)");
		checkArgument(address >= 0, NEGATIVE_LENGTH_MESSAGE);
		checkNotNull(data, "Null data is not allowed.");
		checkArgument(length >= 0, NEGATIVE_ADDRESS_MESSAGE);
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		WriteFlashOperation operation = injector.getInstance(WriteFlashOperation.class);
		checkNotNullOperation(operation, "The Operation writeFlash is not available");
		operation.setData(address, data, length);
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public OperationFuture<Void> writeMac(MacAddress macAddress, long timeout, AsyncCallback<Void> callback) {
		LOG.trace("Write mac (mac address: " + macAddress + ", timeout: " + timeout + "ms)");
		checkNotNull(macAddress, "Null macAdress is not allowed.");
		checkArgument(timeout >= 0, NEGATIVE_TIMEOUT_MESSAGE);
		WriteMacAddressOperation operation = injector.getInstance(WriteMacAddressOperation.class);
		checkNotNullOperation(operation, "The Operation writeMac is not available");
		operation.setMacAddress(macAddress);
		return queue.addOperation(operation, timeout, callback);
	}

	@Override
	public InputStream getInputStream() {
		return inputStreamPipedInputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return new SendOutputStreamWrapper(this, executorService);
	}

	@Override
	public void close() throws IOException {
		deviceInputStreamToPipeCopyWorker.shutdown();
		try {
			// wait until worker has finished execution
			deviceInputStreamToPipeCopyWorkerFuture.get();
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e);
		}
	}

	private void checkNotNullOperation(Operation<?> operation, String message) {
		if (operation == null) {
			throw new UnsupportedOperationException(message);
		}
	}

	private boolean isOperationRunning() {
		return Iterators.any(queue.getOperations().iterator(), new Predicate<Operation<?>>() {
			@Override
			public boolean apply(Operation<?> input) {
				return State.RUNNING.equals(input.getState());
			}
		});
	}
}
