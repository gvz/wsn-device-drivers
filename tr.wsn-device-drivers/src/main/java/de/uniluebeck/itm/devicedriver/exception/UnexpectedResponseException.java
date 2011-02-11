/**********************************************************************************************************************
 * Copyright (c) 2010, coalesenses GmbH                                                                               *
 * All rights reserved.                                                                                               *
 *                                                                                                                    *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the   *
 * following conditions are met:                                                                                      *
 *                                                                                                                    *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following *
 *   disclaimer.                                                                                                      *
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the        *
 *   following disclaimer in the documentation and/or other materials provided with the distribution.                 *
 * - Neither the name of the coalesenses GmbH nor the names of its contributors may be used to endorse or promote     *
 *   products derived from this software without specific prior written permission.                                   *
 *                                                                                                                    *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, *
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE      *
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,         *
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE *
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY   *
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                                *
 **********************************************************************************************************************/

package de.uniluebeck.itm.devicedriver.exception;


/**
*
*/
@SuppressWarnings("serial")
public class UnexpectedResponseException extends Exception {
	private int expectedType = -1;

	private int receivedType = -1;

	/**
	 * Constructor
	 * 
	 * @param expectedType
	 * @param receivedType
	 */
	public UnexpectedResponseException(final int expectedType, final int receivedType) {
		this.expectedType = expectedType;
		this.expectedType = receivedType;
	}
	
	/**
	 * @param msg
	 * @param expectedType
	 * @param receivedType
	 */
	public UnexpectedResponseException(final String msg, final int expectedType, final int receivedType) {
		super(msg);
		this.expectedType = expectedType;
		this.expectedType = receivedType;
	}

	/**
	 * Returns the expected type
	 * 
	 * @return
	 */
	public int getExpectedType() {
		return expectedType;
	}

	/**
	 * Returns the receivedType
	 * 
	 * @return
	 */
	public int getReceivedType() {
		return receivedType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	@Override
	public String getMessage() {
		if (expectedType == -1 || receivedType == -1) {
			return getMessage();
		} else {
			return String.format(getMessage() + " Expected type: 0x%02x, received type: 0x%02x.",expectedType, receivedType);
		}
	}
	
	
}
