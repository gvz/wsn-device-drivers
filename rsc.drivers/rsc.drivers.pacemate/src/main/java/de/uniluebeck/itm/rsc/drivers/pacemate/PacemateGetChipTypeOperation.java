package de.uniluebeck.itm.rsc.drivers.pacemate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uniluebeck.itm.rsc.drivers.core.ChipType;
import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractOperation;
import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractProgressManager;
import de.uniluebeck.itm.rsc.drivers.core.operation.GetChipTypeOperation;

public class PacemateGetChipTypeOperation extends AbstractOperation<ChipType> implements GetChipTypeOperation {

	/**
	 * Logger for this class.
	 */
	private static final Logger log = LoggerFactory.getLogger(PacemateGetChipTypeOperation.class);
	
	private final PacemateDevice device;
	
	public PacemateGetChipTypeOperation(PacemateDevice device) {
		this.device = device;
	}
	
	private ChipType getChipType(final AbstractProgressManager progressManager) throws Exception {
		device.clearStreamData();
		device.autobaud();

		// Wait for a connection
		while (!isCanceled() && !device.waitForConnection()) {
			log.info("Still waiting for a connection");
		}

		// Return with success if the user has requested to cancel this
		// operation
		if (isCanceled()) {
			return null;
		}
		
		// Send chip type read request
		device.sendBootLoaderMessage(Messages.ReadPartIDRequestMessage());

		// Read chip type read response
		final String response = device.receiveBootLoaderReplySuccess(Messages.CMD_SUCCESS);
		final ChipType chipType = ChipType.LPC2136;

		if (response.compareTo("196387") != 0) {
			log.error("Defaulted to chip type LPC2136 (Pacemate). Identification may be wrong." + response);
		}

		log.debug("Chip identified as " + chipType + " (received " + response + ")");
		progressManager.done();
		return chipType;
	}
	
	@Override
	public ChipType execute(final AbstractProgressManager progressManager) throws Exception {
		executeSubOperation(device.createEnterProgramModeOperation(), progressManager.createSub(0.25f));
		ChipType chipType = ChipType.UNKNOWN;
		try {
			chipType = getChipType(progressManager.createSub(0.25f));
		} finally {
			executeSubOperation(device.createLeaveProgramModeOperation(), progressManager.createSub(0.5f));
		}
		return chipType;
	}

}
