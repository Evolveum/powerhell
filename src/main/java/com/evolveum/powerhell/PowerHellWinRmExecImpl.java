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

import java.io.StringWriter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * PowerHell implementation that executes the commands by using plain WinRM.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellWinRmExecImpl extends AbstractPowerHellWinRmImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellWinRmExecImpl.class);
	
	@Override
	protected String getImplementationName() {
		return "WinRM Execution";
	}
	
	@Override
	public String runCommand(String command, Map<String,Object> arguments) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		// winrm4j seems not to be fully ready for client reuse
		if (!isClientConnected()) {
			connectClient();
		}
		
		long tsCommStart = System.currentTimeMillis();
				
		StringWriter writerStdOut = new StringWriter();
		StringWriter writerStdErr = new StringWriter();
		
		String encodedCommandLine = encodeCommand(command, arguments);
		logData("X>", encodedCommandLine);
		
		int exitCode = getClient().command(encodedCommandLine, writerStdOut, writerStdErr);
		
		String out = writerStdOut.toString();
		String err = writerStdErr.toString();
		logData("O<", out);
		logData("E<", err);
    		
		if (exitCode != 0) {
			LOG.error("Exit code received during command execution: {}", exitCode);
			disconnectClient();
			PowerHellExecutionException e = new PowerHellExecutionException("Exit code "+exitCode+" received during command execution", exitCode);
			e.setStdout(out);
			e.setStderr(err);
			throw e;
		}
		
		logExecution(command, tsCommStart);
		
		// winrm4j seems not to be fully ready for client reuse
		disconnectClient();
		
		return out;
	}

	protected String encodeCommand(String command, Map<String,Object> arguments) {
		return encodeCommandExecToString(command, arguments);
	}
	
}
