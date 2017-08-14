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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
	private static final long WAIT_SLEEP_INTERVAL = 50;
	private boolean traceReadProgress = true;
	
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
	public String runCommand(String command, Map<String,Object> arguments) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		long tsCommStart = System.currentTimeMillis();
		
		List<String> encodedCommandLine = encodeCommand(command, arguments);
		logData("X>", encodedCommandLine.stream().collect(Collectors.joining(" ")));
		
		StringBuffer bufferStdOut = new StringBuffer();
		StringBuffer bufferStdErr = new StringBuffer();
		
		ProcessBuilder processBuilder = new ProcessBuilder(encodedCommandLine);
		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			LOG.error("Error executing command: {}", e.getMessage());
			PowerHellExecutionException pe = new PowerHellExecutionException("Error executing command: " + e.getMessage(), e, (Integer)null);
			throw pe;
		}
		InputStreamReader readerStdOut = new InputStreamReader(process.getInputStream());
		InputStreamReader readerStdErr = new InputStreamReader(process.getErrorStream());
		
		char[] buffer = new char[2048];
		
		Integer exitCode = null;
		boolean done = false;
		boolean stdOutOpen = true;
		boolean stdErrOpen = true;
		
		while (!done) {

			// STDOUT
			try {
				if (stdOutOpen && readerStdOut.ready()) {
					int readCount = readerStdOut.read(buffer, 0, buffer.length);
					traceReadProgress("STDOUT", buffer, readCount);
					if (readCount < 0){
	                    stdOutOpen = false;
	                } else if (readCount > 0) {
	                	bufferStdOut.append(buffer, 0, readCount);
	                }
				}
			} catch (IOException e) {
				LOG.error("Error reading from stdout of process {}: {}", encodedCommandLine.get(0), e.getMessage(), e);
				stdOutOpen = false;
			}
			
			// STDERR
			try {
				if (stdErrOpen && readerStdErr.ready()) {
					int readCount = readerStdErr.read(buffer, 0, buffer.length);
					traceReadProgress("STDERR", buffer, readCount);
					if (readCount < 0) {
	                    stdErrOpen = false;
	                } else if (readCount > 0) {
	                	bufferStdErr.append(buffer, 0, readCount);
	                }
				}
			} catch (IOException e) {
				LOG.error("Error reading from stderr of process {}: {}", encodedCommandLine.get(0), e.getMessage(), e);
				stdErrOpen = false;
			}	
			
			// exit status
			try {
				exitCode = process.exitValue();
				done = true;
			} catch (IllegalThreadStateException e) {
				// Trying to read exit code from process that is still running.
				try {
                    Thread.sleep(WAIT_SLEEP_INTERVAL);
                } catch (InterruptedException eIntr) {
                    process.destroy();
                    throw new PowerHellExecutionException("Error waiting for command to finish: " + eIntr.getMessage(), eIntr, (Integer)null);
                }
			}
			
		}
						
		String out = bufferStdOut.toString();
		String err = bufferStdErr.toString();
		logData("O<", out);
		logData("E<", err);
    		
		if (exitCode != 0) {
			LOG.error("Exit code received during command execution: {}", exitCode);
			PowerHellExecutionException e = new PowerHellExecutionException("Exit code "+exitCode+" received during command execution", exitCode);
			e.setStdout(out);
			e.setStderr(err);
			throw e;
		}
		
		logExecution(command, tsCommStart);
		
		return out;
	}

	private void traceReadProgress(String label, char[] buffer, int readCount) {
		if (!traceReadProgress) {
			return;
		}
		if (readCount < 0) {
			LOG.trace("READ {} closed", label);
		} else {
			StringBuffer sbuf = new StringBuffer();
			sbuf.append(buffer, 0, readCount);
			LOG.trace("READ {} {} bytes: {}", label, readCount, sbuf.toString());
		}
	}

	protected List<String> encodeCommand(String command, Map<String, Object> arguments) {
		return encodeCommandExecToList(command, arguments);
	}
}
