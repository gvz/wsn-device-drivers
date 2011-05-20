package de.uniluebeck.itm.wsn.drivers.factories;

import de.uniluebeck.itm.wsn.drivers.core.Device;
import de.uniluebeck.itm.wsn.drivers.core.async.DeviceAsync;
import de.uniluebeck.itm.wsn.drivers.core.async.OperationQueue;
import de.uniluebeck.itm.wsn.drivers.core.async.QueuedDeviceAsync;
import de.uniluebeck.itm.wsn.drivers.core.async.thread.PausableExecutorOperationQueue;
import de.uniluebeck.itm.wsn.drivers.core.serialport.SerialPortConnection;

public abstract class DeviceAsyncFactory {

	public static DeviceAsync create(final DeviceType deviceType, final SerialPortConnection connection) {
		return create(deviceType, connection, new PausableExecutorOperationQueue());
	}
	
	public static DeviceAsync create(final DeviceType deviceType, final SerialPortConnection connection, final OperationQueue operationQueue) {
		Device<SerialPortConnection> device = DeviceFactory.create(deviceType, connection);
		return new QueuedDeviceAsync(operationQueue, device);
	}

	public static DeviceAsync create(final String deviceType, final SerialPortConnection connection) {
		return create(DeviceType.fromString(deviceType), connection);
	}
	
	public static DeviceAsync create(final String deviceType, final SerialPortConnection connection, final OperationQueue operationQueue) {
		return create(DeviceType.fromString(deviceType), connection, operationQueue);
	}

}