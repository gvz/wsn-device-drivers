package de.uniluebeck.itm.wsn.drivers.telosb;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.uniluebeck.itm.util.StringUtils;
import de.uniluebeck.itm.util.TimeDiff;
import de.uniluebeck.itm.wsn.drivers.core.exception.*;
import de.uniluebeck.itm.wsn.drivers.core.serialport.SerialPortConnection;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Singleton
public class BSLTelosb {

	private static final Logger log = LoggerFactory.getLogger(BSLTelosb.class);

	/**
	 * Possible values for msp430 baud rate
	 */
	public static enum BaudRate {
		/**
		 * 9600 baud (initial rate)
		 */
		Baud9600,
		/**
		 * 19200 baud
		 */
		Baud19200,
		/**
		 * 38000 baud
		 */
		Baud38000;

		public String toString() {
			if (this == Baud19200) {
				return "19200";
			} else if (this == Baud38000) {
				return "38400";
			} else {
				return "9600";
			}
		}

		/**
		 * @return int value of baud rate
		 */
		public int toInt() {
			if (this == Baud19200) {
				return 19200;
			} else if (this == Baud38000) {
				return 38400;
			} else {
				return 9600;
			}
		}
	}

	/** boot loader command ids */
	/**
	 * mass erase
	 */
	public static final int CMD_MASSERASE = 0x18;

	/**
	 * transmit password
	 */
	public static final int CMD_TXPASSWORD = 0x10;

	/**
	 * transmit data block
	 */
	public static final int CMD_TXDATABLOCK = 0x12;

	/**
	 * load program counter
	 */
	public static final int CMD_LOADPC = 0x1A;

	/**
	 * receive data block
	 */
	public static final int CMD_RXDATABLOCK = 0x14;

	/**
	 * receive bsl version
	 */
	public static final int CMD_RXBSLVERSION = 0x1E;

	/**
	 * change baud rate
	 */
	public static final int CMD_CHANGEBAUD = 0x20;

	/**
	 * ACK
	 */
	public static final int DATA_ACK = 0x90;

	/**
	 * NO ACK
	 */
	public static final int DATA_NACK = 0xA0;

	/* bsl synchronization byte */
	private final int BSL_SYNC = 0x80;

	/* bsl synchronization acknowledge byte */
	private final int SYNC_ACK = 0x90;

	/* hdr byte (1st byte of any bsl command message) */
	private final int BSL_HDR = 0x80;

	/**
	 * byte value constants for bsl communication
	 */
	/* time out for waiting for a message reply of the connected device */
	private static final int DEFAULT_REPLY_TIMEOUT_MILLIS = 2000;

	/* set to true if the patch required by the bsl was loaded into device memory
	 * and is ready to be executed when needed */
	private boolean bslPatchLoaded = false;

	/* current baud rate used for communicating with the bsl */
	private BaudRate currentBaudRate = BaudRate.Baud9600;

	public final Object dataAvailableMonitor = new Object();

	int oldBaudRate;

	boolean bslBaudRateSet = false;

	private final SerialPortConnection connection;

	@Inject
	public BSLTelosb(SerialPortConnection connection) {
		this.connection = connection;
	}

	/**
	 * Initializes bsl communication by resetting the device.
	 *
	 * @return true if BSL was started successfully
	 */
	public boolean invokeBSL() {

		TelosI2CCom i2cCom = new TelosI2CCom(connection.getSerialPort());

		log.debug("invokeBSL()");

		// send commands via I2C to reset device and invoke boot loader
		i2cCom.writeCommand(0, 1);
		i2cCom.writeCommand(0, 3);
		i2cCom.writeCommand(0, 1);
		i2cCom.writeCommand(0, 3);
		i2cCom.writeCommand(0, 2);
		i2cCom.writeCommand(0, 0);
		i2cCom.writeCommand(0, 0);

		waitForMpOscillatorToStabilize();


		return true;
	}

	private void waitForMpOscillatorToStabilize() {
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			log.error("InterruptedException while waiting for mp oscillator to stabilize: {}", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Reset the device.
	 *
	 * @return true if reset successfully
	 */
	public boolean reset() {
		TelosI2CCom i2cCom = new TelosI2CCom(connection.getSerialPort());

		log.debug("reset()");

		// reset device by sending commands via I2C
		i2cCom.writeCommand(0, 3);
		i2cCom.writeCommand(0, 2);
		i2cCom.writeCommand(0, 0);

		waitForMpOscillatorToStabilize();

		// set baud rate back to standard when initializing bsl communication the next time
		currentBaudRate = BaudRate.Baud9600;

		return true;
	}


	/**
	 * Send BSL command with given address, length and data.
	 * Return the received answer of the bsl.
	 *
	 * @param cmd
	 * 		command byte (CMD)
	 * @param address
	 * 		high and low address bytes (AL and AH)
	 * @param pureDataLength
	 * 		number of pure data bytes or possibly other info (LL and LH)
	 * @param data
	 * 		array of data bytes (D1...Dn), may be null
	 * @param wait
	 * 		if true, BSL sync will be repeated forever until it succeeds
	 *
	 * @throws UnexpectedResponseException
	 */
	public void sendBSLCommand(int cmd, int address, int pureDataLength, byte data[], boolean wait)
			throws TimeoutException, IOException, UnexpectedResponseException {
		byte[] dataFrame;
		int extendedLength;
		int checksum;
		int numDataBytes;
		@SuppressWarnings("unused")
		String frameString = "";

		if (data == null) {
			numDataBytes = 0;
		} else {
			// number of data bytes is restricted to 250 max
			if (data.length > 250) {
				log.warn("Warning: number of pure data bytes of bsl command exceeds maximum of 250. " +
						(data.length - 250) + " bytes will be truncated."
				);
				pureDataLength = 250;
			}
			numDataBytes = Math.min(250, data.length);
		}

		// compute extended data length including LL,LH,AL,AH 
		extendedLength = 4 + numDataBytes + (numDataBytes % 2);

		// create data frame to send
		dataFrame = new byte[4 + extendedLength + 2];
		dataFrame[0] = (byte) BSL_HDR;    // HDR
		dataFrame[1] = (byte) cmd;    //CMD
		dataFrame[2] = (byte) extendedLength;    // L1
		dataFrame[3] = (byte) extendedLength;    // L2
		dataFrame[4] = (byte) (address & 0xFF);    // AL
		dataFrame[5] = (byte) ((address >> 8) & 0xFF);    // AH
		dataFrame[6] = (byte) (pureDataLength & 0xFF);        // LL
		dataFrame[7] = (byte) ((pureDataLength >> 8) & 0xFF);        // LH

		if (data != null) {
			// copy data bytes to frame
			System.arraycopy(data, 0, dataFrame, 8, numDataBytes);
		}

		// in case of uneven data length, append one byte to make it even
		if (numDataBytes % 2 != 0) {
			dataFrame[4 + extendedLength - 1] = (byte) 0xFF;
		}

		// calculate and add checksum
		checksum = calcChecksum(dataFrame);
		dataFrame[4 + extendedLength] = (byte) (checksum & 0xFF);    // CKL
		dataFrame[4 + extendedLength + 1] = (byte) ((checksum >> 8) & 0xFF);     // CKH

		// synchronize BSL before sending a command
		if (!bslSynchronize(wait)) {
			throw new UnexpectedResponseException("BSL sync failed while sending bsl command.", DATA_ACK, DATA_NACK);
		}

		// send frame
		OutputStream outputStream = connection.getOutputStream();
		for (final byte aDataFrame : dataFrame) {
			outputStream.write(aDataFrame);
		}
		outputStream.flush();

//		if (log.isDebugEnabled()) {
//			for (int i=0; i < dataFrame.length; i++) {
//				frameString += String.format(" 0x%02x ", dataFrame[i]);	
//			}
//			log.debug("Transmitted data frame to bsl: " + frameString);
//		}
	}

	/**
	 * Receive bsl reply to a previously sent command
	 *
	 * @return bsl reply (ACK, NACK or replied data D0..Dn)
	 *
	 * @throws IOException
	 * @throws TimeoutException
	 * @throws InvalidChecksumException
	 * @throws ReceivedIncorrectDataException
	 * @throws UnexpectedResponseException
	 */
	public byte[] receiveBSLReply()
			throws IOException, TimeoutException, InvalidChecksumException, ReceivedIncorrectDataException,
			UnexpectedResponseException {
		byte[] dataNoHeader;
		int reply;
		byte[] tempData;
		byte[] result;
		int receivedChecksumL;
		int receivedChecksumH;
		int lengthFrameData;
		int numBytesRead;
		int numTries;
		int checksum;
		byte[] dataFrame;
		String frameString;

		InputStream inputStream = connection.getInputStream();
		waitDataAvailable(inputStream, DEFAULT_REPLY_TIMEOUT_MILLIS);
		reply = inputStream.read();

		if (reply == DATA_ACK) {
			// acknowledge received
//			if (log.isDebugEnabled()) {
//				log.debug("Received bsl ACK");
//			}
			result = (new byte[]{(byte) reply});
		} else if (reply == DATA_NACK) {
			// no acknowledge received
			if (log.isDebugEnabled()) {
				log.debug("Received bsl NACK");
			}
			result = (new byte[]{(byte) reply});
		} else if (reply == BSL_HDR) {
			// receiving a data frame
			dataFrame = new byte[4];
			dataFrame[0] = (byte) reply;

			// read header
			numBytesRead = 0;
			numTries = 0;
			while (true) {
				waitDataAvailable(inputStream, DEFAULT_REPLY_TIMEOUT_MILLIS);
				numBytesRead += inputStream.read(dataFrame, 1 + numBytesRead, 3 - numBytesRead);
				if (numBytesRead != 3) {
					if (numTries < 3) {
//						if (log.isDebugEnabled()) {
//							log.debug("Time out receiving bsl reply, retrying...");
//						}
					} else {
						frameString = "";
						for (int i = 0; i < numBytesRead; i++) {
							frameString += String.format(" 0x%02x ", dataFrame[i]);
						}
						throw new TimeoutException(
								"Time out receiving BSL reply data.\nData received so far: " + frameString
						);
					}
				} else {
					break;
				}
				numTries++;
			}

			// check if frame header is correct
			if ((dataFrame[1] != 0x00) || (dataFrame[2] != dataFrame[3])) {
				if (log.isDebugEnabled()) {
					log.debug("Header of received bsl reply is corrupt");
				}
				throw new ReceivedIncorrectDataException("Header of received BSL reply is corrupt.");
			}

			// extend array length for received data
			tempData = new byte[dataFrame.length];
			System.arraycopy(dataFrame, 0, tempData, 0, dataFrame.length);
			lengthFrameData = (0xFF & dataFrame[2]);
			dataFrame = new byte[4 + lengthFrameData];
			System.arraycopy(tempData, 0, dataFrame, 0, tempData.length);

			// read frame data excluding the checksum
			numBytesRead = 0;
			numTries = 0;
			while (true) {
				waitDataAvailable(inputStream, DEFAULT_REPLY_TIMEOUT_MILLIS);
				numBytesRead += inputStream.read(dataFrame, 4 + numBytesRead, lengthFrameData - numBytesRead);
				if (numBytesRead != lengthFrameData) {
					if (numTries < 5) {
//						if (log.isDebugEnabled()) {
//							log.debug("Time out receiving bsl reply. Retrying to receive data...");
//						}
					} else {
						frameString = "";
						for (int i = 0; i < numBytesRead + 4; i++) {
							frameString += String.format(" 0x%02x ", dataFrame[i]);
						}
						throw new TimeoutException("Time out receiving BSL reply data (was expecting "
								+ lengthFrameData + "bytes but received " + numBytesRead + " instead. "
								+ "\nData Received so far: " + frameString
						);
					}
				} else {
					break;
				}
				numTries++;
			}

			// read and validate checksum
			waitDataAvailable(inputStream, DEFAULT_REPLY_TIMEOUT_MILLIS);
			receivedChecksumL = inputStream.read();
			receivedChecksumH = inputStream.read();
			if (receivedChecksumH == -1 || receivedChecksumL == -1) {
				frameString = "";
				for (final byte aDataFrame : dataFrame) {
					frameString += String.format(" 0x%02x ", aDataFrame);
				}
				throw new TimeoutException("Time out receiving BSL reply: missing checksum in data frame. " +
						"\nData received so far: " + frameString
				);
			}
//			if (log.isDebugEnabled()) {
//				frameString = "";
//				for (int i=0; i<dataFrame.length; i++) {
//					frameString += String.format(" 0x%02x ", dataFrame[i]);
//				}
//				log.debug("Received bsl reply:"+frameString);
//			}
			checksum = calcChecksum(dataFrame);

			if ((receivedChecksumL != (checksum & 0xFF)) ||
					(receivedChecksumH != ((checksum >> 8) & 0xFF))) {
				throw new InvalidChecksumException(String.format("Wrong checksum receiving BSL reply: " +
						"was: 0x%02x 0x%02x but should be: 0x%02x 0x%02x", receivedChecksumL, receivedChecksumH,
						checksum & 0xFF, (checksum >> 8) & 0xFF
				)
				);
			}

			// complete frame received correctly, return data without header
			dataNoHeader = new byte[dataFrame.length - 4];
			System.arraycopy(dataFrame, 4, dataNoHeader, 0, dataNoHeader.length);
			result = dataNoHeader;

		} else {
			throw new UnexpectedResponseException("Received unknown BSL reply.", DATA_ACK, (0xFF & reply));
		}

		return result;
	}

	/**
	 * Transmit 32 byte password to the boot loader to unlock all password protected commands
	 *
	 * @param password
	 * 		the password
	 * @param wait
	 * 		if true, bsl sync will try forever
	 *
	 * @return true if received ACK, false if received NACK
	 *
	 * @throws IOException
	 * @throws TimeoutException
	 * @throws InvalidChecksumException
	 * @throws ReceivedIncorrectDataException
	 * @throws UnexpectedResponseException
	 */
	public boolean transmitPassword(byte[] password, boolean wait)
			throws IOException, TimeoutException, InvalidChecksumException, ReceivedIncorrectDataException,
			UnexpectedResponseException {
		byte[] pwData;
		byte[] reply;

		if (log.isDebugEnabled()) {
			log.debug("transmitPassword()");
		}

		if (password == null) {
			// transmit default password (all bytes 0xFF)
			pwData = new byte[32];
			for (int i = 0; i < pwData.length; i++) {
				pwData[i] = (byte) 0xFF;
			}

			if (log.isDebugEnabled()) {
				log.debug("Default password transmitted.");
			}
		} else {
			// password must consist of 32 bytes
			if (password.length != 32) {
				log.error(
						"Error transmitting BSL password: password length of " + password.length + " bytes is not correct"
				);
				return false;
			} else {
				pwData = password;

				if (log.isDebugEnabled()) {
					log.debug("Password [" + StringUtils.toHexString(password) + "] transmitted.");
				}
			}
		}

		sendBSLCommand(CMD_TXPASSWORD,
				0,    // start address is always 0xFFE0
				0,     // password length is always 32 bytes
				pwData,
				wait
		);

		reply = receiveBSLReply();

		if ((reply[0] & 0xFF) == DATA_ACK) {
			return true;
		} else if ((reply[0] & 0xFF) == DATA_NACK) {
			return false;
		} else {
			throw new UnexpectedResponseException("Received unknown reply while sending BSL password.", DATA_ACK,
					(reply[0] & 0xFF)
			);
		}
	}

	/**
	 * Prepare BSL patch by directing program counter of the mote to start address of the patch code.
	 * This patch is required by BSL version 1.10 to execute commands for reading/writing blocks correctly.
	 *
	 * @return true if ACK received, false if NACK received
	 *
	 * @throws IOException
	 * @throws TimeoutException
	 * @throws InvalidChecksumException
	 * @throws ReceivedIncorrectDataException
	 * @throws UnexpectedResponseException
	 */
	public boolean executeBSLPatch()
			throws IOException, TimeoutException, InvalidChecksumException, ReceivedIncorrectDataException,
			UnexpectedResponseException {
		byte[] reply;

		if (bslPatchLoaded) {
			sendBSLCommand(CMD_LOADPC, 0x0220, 0, null, false);

			reply = receiveBSLReply();

			if (reply[0] == (0xFF & DATA_ACK)) {
				return true;
			} else if (reply[0] == (0xFF & DATA_NACK)) {
				return false;
			} else {
				throw new UnexpectedResponseException("Received unknown reply while executing BSL patch.", DATA_ACK,
						(reply[0] & 0xFF)
				);
			}
		}

		return true;
	}

	/**
	 * Verify a memory block of given length against given data or 0xFF (erased data)
	 *
	 * @param address
	 * 		address of data block to verify
	 * @param length
	 * 		number of bytes to verify
	 * @param data
	 * 		data against which to verify or null if to verify against 0xFF
	 *
	 * @return true if checked data matches supplied data, false otherwise
	 *
	 * @throws TimeoutException
	 * @throws InvalidChecksumException
	 * @throws IOException
	 * @throws ReceivedIncorrectDataException
	 * @throws UnexpectedResponseException
	 */
	public boolean verifyBlock(int address, int length, byte[] data)
			throws TimeoutException, InvalidChecksumException, IOException, ReceivedIncorrectDataException,
			UnexpectedResponseException {
		byte[] reply;
		int checkedLength;
		String dataString = "";

//		if (log.isDebugEnabled()) {
//			if (data != null) {
//				log.debug(String.format("Verifying data at 0x%02x, %d bytes", address, length));
//			} else {
//				log.debug(String.format("Verifying at 0x%02x that %d bytes are erased correctly", address, length));
//			}
//		}

		checkedLength = length;

		if (data != null) {
			if (length > data.length) {
				log.warn(
						"Warning: supplied length for block verification(" + length + ") does not match length of data."
				);
				checkedLength = data.length;
			}
		}

		// execute bsl patch
		executeBSLPatch();

		// receive data block
		sendBSLCommand(CMD_RXDATABLOCK, address, checkedLength, null, false);

		reply = receiveBSLReply();

		if (((0xFF & reply[0]) == DATA_NACK) && reply.length == 1) {
			throw new ReceivedIncorrectDataException("Failed to verify data block: received NACK.");
		}
		if (reply.length != length) {
			for (int i = 0; i < reply.length; i++) {
				dataString += String.format("0x%02x ", reply[i]);
			}
			throw new ReceivedIncorrectDataException("Failed to verify data block: " +
					"length of bsl reply is unexpected. Reply: " + dataString
			);
		}

		// verify data
		for (int i = 0; i < reply.length; i++) {
			if (data == null) {
				// check against 0xFF
				if ((reply[i] & 0xFF) != 0xFF) {
					if (log.isDebugEnabled()) {
						log.debug(String.format("Error validating block at 0x%02x, byte %d. " +
								"Was: 0x%02x, but should be: 0xFF", address, i + 1, reply[i]
						)
						);
					}
					return false;
				}
			} else {
				// check against supplied data
				if (reply[i] != data[i]) {
					if (log.isDebugEnabled()) {
						log.debug(String.format("Error validating block at 0x%02x, byte %d. " +
								"Was: 0x%02x, but should be: 0x%02x", address, i + 1, reply[i], data[i]
						)
						);
					}
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Change the baud rate for data transmissions to the boot loader.
	 * Valid baud rates are 9600, 19200, 38000. Baud rate for bsl communication
	 * can only be changed by using this bsl command.
	 *
	 * @param newBaud
	 * 		new baud rate to set
	 *
	 * @return true if baud rate was changed successfully, false if no ACK was received
	 *
	 * @throws TimeoutException
	 * @throws UnexpectedResponseException
	 * @throws IOException
	 * @throws InvalidChecksumException
	 * @throws ReceivedIncorrectDataException
	 */
	public boolean changeBaudRate(BaudRate newBaud)
			throws TimeoutException, UnexpectedResponseException, IOException, InvalidChecksumException,
			ReceivedIncorrectDataException {
		byte[] reply;
		int a;
		int l;
		BaudRate newBaudRate;

		switch (newBaud) {
			case Baud19200:
				a = 0x85E0;
				l = 0x0001;
				newBaudRate = BaudRate.Baud19200;
				break;
			case Baud38000:
				a = 0x87E0;
				l = 0x0002;
				newBaudRate = BaudRate.Baud38000;
				break;
			case Baud9600:
			default:
				a = 0x8580;
				l = 0x0000;
				newBaudRate = BaudRate.Baud9600;
		}


		// send bsl command
		sendBSLCommand(CMD_CHANGEBAUD, a, l, null, false);
		reply = receiveBSLReply();

		if ((0xFF & reply[0]) == DATA_NACK) {
			log.error("Failed to change baud rate, received NACK.");
			return false;
		} else if ((0xFF & reply[0]) == DATA_ACK) {
			log.debug("Changing baud rate to " + newBaudRate + "...");
		} else if (reply.length != 1) {
			log.error("Failed to change baud rate, received unexpected reply of length " + reply.length);
			return false;
		}

		// set new baud rate for serial port
		SerialPort serialPort = connection.getSerialPort();
		try {
			serialPort.setSerialPortParams(newBaudRate.toInt(),
					serialPort.getDataBits(),
					serialPort.getStopBits(),
					serialPort.getParity()
			);
		} catch (UnsupportedCommOperationException e) {
			throw new IOException("Error changing baud rate: " + e);
		}

		currentBaudRate = newBaudRate;

		return true;
	}

	/**
	 * Change the baud rate and parity information for data transmissions.
	 *
	 * @param newBaudRate
	 * 		new baud rate to set
	 *
	 * @return true if baud rate was changed successfully, false if no ACK was received
	 *
	 * @throws IOException
	 */
	public boolean changeComPort(int newBaudRate, int newParity) throws IOException {
		// set new baud rate for serial port
		SerialPort serialPort = connection.getSerialPort();
		try {
			serialPort.setSerialPortParams(newBaudRate,
					serialPort.getDataBits(),
					serialPort.getStopBits(),
					newParity
			);
		} catch (UnsupportedCommOperationException e) {
			throw new IOException("Error changing baud rate: " + e);
		}

		return true;
	}

	/*
		 * Send synchronization byte to initiate sending of a bsl command
		 * @param wait if true, retry infinitely often to sync
		 * @return true, if sync was successful (ACK received), false otherwise
		 */
	private boolean bslSynchronize(boolean wait) throws TimeoutException, IOException {
		int answer;
		int maxTries = 10;

		//TODO: check for right baud rate

		while (wait || (maxTries > 0)) {
			// clear input stream
			flushInputStream();

			maxTries--;

			// send sync byte and read answer
			connection.getOutputStream().write(BSL_SYNC);
			answer = connection.getInputStream().read();

			if (answer == SYNC_ACK) {
				// ack received
//				if (log.isDebugEnabled()) {
//					log.debug("BSL sync: received ACK");
//				}
				return true;
			} else if (answer == -1) {
				// nothing received
				//TODO: retry to reset?
				if (maxTries == 0) {
					// was last try
//					if (log.isDebugEnabled()) {
//						log.debug("BSL sync time out");
//					}
					throw new TimeoutException("Time out waiting for BSL sync ACK.");
				}

				// retry to sync
				if (log.isDebugEnabled()) {
					log.debug("BSL sync time out, retry...");
				}
			} else {
				// no ack received
//				if (log.isDebugEnabled()) {
//					log.debug(String.format("BSL sync: missing ACK (answer was: 0x%02x)",answer));
//				}
				return false;
			}
		}

		return false;
	}

	/*
		 * Calculate checksum of a complete given bsl command message
		 */
	private int calcChecksum(byte dataFrame[]) {
		int checksum = 0;

		for (int i = 0; i < (dataFrame.length / 2); i++) {
			checksum = checksum ^ ((0xFF & dataFrame[2 * i]) + 256 * (0xFF & dataFrame[2 * i + 1]));
		}
		checksum = ~checksum;

		return checksum;
	}

	/*
		 * Wait for data being available from serial port. Throw TimeoutException after
		 * specified time out.
		 */
	private int waitDataAvailable(InputStream inputStream, int timeoutMillis) throws TimeoutException, IOException {
		TimeDiff timeDiff = new TimeDiff();
		int avail = 0;

		while (inputStream != null && (avail = inputStream.available()) == 0) {
			if (timeoutMillis > 0 && timeDiff.ms() >= timeoutMillis) {
				throw new TimeoutException(
						"Timeout waiting for data (waited: " + timeDiff.ms() + ", timeoutMs:" + timeoutMillis + ")"
				);
			}

			synchronized (dataAvailableMonitor) {
				try {
					dataAvailableMonitor.wait(50);
				} catch (InterruptedException e) {
					log.error("Error " + e);
				}
			}
		}
		return avail;
	}

	/*
		 * Flush the input buffer.
		 */
	private void flushInputStream() {
		long i;

		//log.debug("flushInputStream()");

		try {
			InputStream inputStream = connection.getInputStream();
			while ((i = inputStream.available()) > 0) {
				//noinspection ResultOfMethodCallIgnored
				inputStream.skip(i);
			}
		} catch (IOException e) {
			log.warn("Error while flushing serial input stream: " + e, e);
		}
	}

	/**
	 * Set the baud rate to the appropriate value necessary for bsl communication, in case another
	 * baud rate was used before for communication with the os.
	 *
	 * @throws IOException
	 */
	public void setBslBaudRate() throws IOException {
		if (bslBaudRateSet) {
			return;
		}
		SerialPort serialPort = connection.getSerialPort();
		oldBaudRate = serialPort.getBaudRate();
		try {
			serialPort.setSerialPortParams(currentBaudRate.toInt(),
					serialPort.getDataBits(),
					serialPort.getStopBits(),
					serialPort.getParity()
			);
			log.debug("Baud rate changed for bsl communication from " + oldBaudRate + " to " + currentBaudRate
					.toInt() + "."
			);
		} catch (UnsupportedCommOperationException e) {
			throw new IOException(e.getMessage());
		}
		bslBaudRateSet = true;
	}


	/**
	 * Restore the external baud rate used by the serial port before
	 * the baud rate was changed for bsl communication.
	 *
	 * @throws IOException
	 */
	public void restoreNonBslBaudRate() throws IOException {
		if (!bslBaudRateSet) {
			return;
		}
		SerialPort serialPort = connection.getSerialPort();
		try {
			serialPort.setSerialPortParams(oldBaudRate,
					serialPort.getDataBits(),
					serialPort.getStopBits(),
					serialPort.getParity()
			);
			log.debug("Baud rate changed back after bsl communication to " + oldBaudRate + ".");
		} catch (UnsupportedCommOperationException e) {
			throw new IOException(e.getMessage());
		}
		bslBaudRateSet = false;
	}

	public void writeFlash(int address, byte[] bytes, int len)
			throws IOException, FlashProgramFailedException, TimeoutException, InvalidChecksumException,
			ReceivedIncorrectDataException, UnexpectedResponseException {
		sendBSLCommand(BSLTelosb.CMD_TXDATABLOCK, address, len, bytes, false);
		final byte[] reply = receiveBSLReply();
		if ((reply[0] & 0xFF) != BSLTelosb.DATA_ACK) {
			throw new FlashProgramFailedException("Failed to program flash: received no ACK");
		}
	}
}