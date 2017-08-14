/**
 * Copyright (c) 2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.powerhell;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;

import javax.net.ssl.HostnameVerifier;
import javax.xml.bind.DatatypeConverter;

import org.apache.cxf.interceptor.Fault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.Command;
import io.cloudsoft.winrm4j.client.WinRmClient;

/**
 * <p>
 * TODO
 * </p> 
 * 
 * @author semancik
 */
public abstract class AbstractPowerHellImpl implements PowerHell {
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractPowerHellImpl.class);
		
	protected abstract String getImplementationName();

	protected void processFault(String message, Fault e) throws PowerHellSecurityException, PowerHellCommunicationException {
		// Fault does not have useful information on its own. Try to mine out something useful.
		Throwable cause = e.getCause();
		if (cause instanceof IOException) {
			if (cause.getMessage() != null && cause.getMessage().contains("Authorization loop detected")) {
				throw new PowerHellSecurityException(cause.getMessage(), e);
			}
		}
		throw new PowerHellCommunicationException(message + ": " + e.getMessage(), e);
	}
		
	protected String encodeUtf16Base64(String command) {
		byte[] bytes = command.getBytes(Charset.forName("UTF-16LE"));
        return DatatypeConverter.printBase64Binary(bytes);
	}
	
	protected String encodePowerShell(String psScript) {
		return "powershell -EncodedCommand "+encodeUtf16Base64(psScript);
	}
	
	protected void logData(String prefix, String data) {
		if (LOG.isTraceEnabled()) {
			if (data != null && !data.isEmpty()) {
				LOG.trace("{0} {1}", prefix, data);
			}
		}
	}
	
	protected void logExecution(String outCommandLine, long tsCommStart) {
		long tsCommStop = System.currentTimeMillis();
		LOG.debug("Command {} run time: {} ms", outCommandLine, tsCommStop-tsCommStart);
	}

}
