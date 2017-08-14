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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * PowerHell implementation that executes the commands by using plain WinRM.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellLocalExecImpl extends AbstractPowerHellImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellLocalExecImpl.class);
	
	@Override
	protected String getImplementationName() {
		return "Local Execution";
	}
	
	@Override
	public void connect()
			throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		// Nothing to do
	}

	@Override
	public int disconnect() {
		// Nothing to do
		return 0;
	}

	
	@Override
	public String runCommand(String outCommandLine) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		long tsCommStart = System.currentTimeMillis();
		
		String encodedCommandLine = encodeCommand(outCommandLine);
		logData("X>", encodedCommandLine);
		
		ProcessBuilder processBuilder = new ProcessBuilder(encodedCommandLine);
		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			LOG.error("Error executing command: {}", e.getMessage());
			PowerHellExecutionException pe = new PowerHellExecutionException("Error executing command: " + e.getMessage(), e, (Integer)null);
			throw pe;
		}
		BufferedReader readerStdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedReader readerStdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			
		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			LOG.error("Error waiting for command to finish: {}", e.getMessage());
			PowerHellExecutionException pe = new PowerHellExecutionException("Error waiting for command to finish: " + e.getMessage(), e, (Integer)null);
			throw pe;
		}
				
		String out = readerStdOut.lines().collect(Collectors.joining());
		String err = readerStdErr.lines().collect(Collectors.joining());
		logData("O<", out);
		logData("E<", err);
    		
		if (exitCode != 0) {
			LOG.error("Exit code received during command execution: {}", exitCode);
			PowerHellExecutionException e = new PowerHellExecutionException("Exit code "+exitCode+" received during command execution", exitCode);
			e.setStdout(out);
			e.setStderr(err);
			throw e;
		}
		
		logExecution(outCommandLine, tsCommStart);
		
		return out;
	}

	protected String encodeCommand(String outCommandLine) {
		// No command encoding is needed for plain WinRM execution
		return outCommandLine;
	}

}
