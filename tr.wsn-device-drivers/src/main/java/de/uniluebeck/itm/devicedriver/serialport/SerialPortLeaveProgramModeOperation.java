package de.uniluebeck.itm.devicedriver.serialport;

import de.uniluebeck.itm.devicedriver.Monitor;
import de.uniluebeck.itm.devicedriver.operation.AbstractLeaveProgramModeOperation;
import de.uniluebeck.itm.devicedriver.serialport.SerialPortConnection.SerialPortMode;

public class SerialPortLeaveProgramModeOperation extends AbstractLeaveProgramModeOperation {

private final SerialPortConnection connection;
	
	public SerialPortLeaveProgramModeOperation(SerialPortConnection connection) {
		this.connection = connection;
	}
	
	@Override
	public Void execute(Monitor monitor) throws Exception {
		connection.flush();
		monitor.onProgressChange(0.5f);
		connection.setSerialPortMode(SerialPortMode.NORMAL);
		monitor.onProgressChange(1.0f);
		return null;
	}

}