package de.uniluebeck.itm.devicedriver.jennic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uniluebeck.itm.devicedriver.Monitor;
import de.uniluebeck.itm.devicedriver.operation.AbstractReadFlashOperation;
import de.uniluebeck.itm.tr.util.StringUtils;

public class JennicReadFlashOperation extends AbstractReadFlashOperation {

	/**
	 * Logger for this class.
	 */
	private static final Logger log = LoggerFactory.getLogger(JennicReadFlashOperation.class);
	
	private final JennicDevice device;
	
	public JennicReadFlashOperation(JennicDevice device) {
		this.device = device;
	}
	
	private byte[] readFlash(final Monitor monitor) throws Exception {
		// Wait for a connection
		while (!isCanceled() && !device.waitForConnection()) {
			log.info("Still waiting for a connection");
		}

		// Return with success if the user has requested to cancel this
		// operation
		if (isCanceled()) {
			return null;
		}

		// Read all sectors
		final int address = getAddress();
		final int length = getLength();
		final byte flashData[] = new byte[length];
		final int sectorEnd = address + length;
		int sectorStart = address;

		while (sectorStart < sectorEnd) {
			// Determine length of the data block to read
			final int blockSize = sectorStart + 32 > sectorEnd ? length : 32;

			// Read data block
			byte[] data = device.readFlash(sectorStart, blockSize);
			System.arraycopy(data, 0, flashData, sectorStart - address, data.length);
			
			// Notify listeners
			float progress = ((float) (sectorStart - address)) / length;
			monitor.onProgressChange(progress);

			// Increment start address
			sectorStart += blockSize;

		}
		log.debug("Done, result is: " + StringUtils.toHexString(flashData));
		return flashData;
	}
	
	@Override
	public byte[] execute(Monitor monitor) throws Exception {
		byte[] data = null;
		// Enter programming mode
		executeSubOperation(device.createEnterProgramModeOperation(), monitor);
		try {
			data = readFlash(monitor);
		} finally {
			executeSubOperation(device.createLeaveProgramModeOperation(), monitor);
		}
		return data;
	}
	


}
