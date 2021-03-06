package de.uniluebeck.itm.wsn.drivers.core.serialport;

import gnu.io.SerialPort;
import de.uniluebeck.itm.wsn.drivers.core.Connection;

/**
 * Connection type that can be used when a <code>SerialPort</code> instance is used.
 * 
 * @author Malte Legenhausen
 */
public interface SerialPortConnection extends Connection {
	
	/**
	 * Serial port modes for normal usage and programming.
	 */
	public enum SerialPortMode {
		
		/**
		 * Serial port mode for normal operations.
		 */
		NORMAL, 
		
		/**
		 * Serial port mode for programming.
		 */
		PROGRAM
	}
	
	/**
	 * Returns the <code>SerialPort</code> instance.
	 * 
	 * @return SerialPort instance.
	 */
	SerialPort getSerialPort();
	
	/**
	 * Sets the serial port mode for normal operations or programming.
	 * 
	 * @param mode Set the port to normal or programming mode.
	 */
	void setSerialPortMode(SerialPortMode mode);

}
