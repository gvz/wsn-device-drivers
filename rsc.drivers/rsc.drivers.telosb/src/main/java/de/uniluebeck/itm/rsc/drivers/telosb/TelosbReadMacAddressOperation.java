package de.uniluebeck.itm.rsc.drivers.telosb;

import de.uniluebeck.itm.rsc.drivers.core.MacAddress;
import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractOperation;
import de.uniluebeck.itm.rsc.drivers.core.operation.AbstractProgressManager;
import de.uniluebeck.itm.rsc.drivers.core.operation.ReadMacAddressOperation;

public class TelosbReadMacAddressOperation extends AbstractOperation<MacAddress> implements ReadMacAddressOperation {

	@Override
	public MacAddress execute(final AbstractProgressManager progressManager) throws Exception {
		throw new UnsupportedOperationException("Read mac address it not available.");
	}
}
