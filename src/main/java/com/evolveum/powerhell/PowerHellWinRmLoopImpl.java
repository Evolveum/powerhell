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

import javax.xml.ws.soap.SOAPFaultException;

import org.apache.cxf.interceptor.Fault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.Command;

/**
 * <p>
 * Simplistic shell emulation written in PowerShell and executed remotely
 * using WinRM (WS-MAN). PowerHell has ability to initialize PowerShell
 * environment once and then execute any number of commands in an interactive
 * fashion. This is needed especially for Exchange. For some strange reasons
 * the Exchange PowerShell snap-in takes extremely long time to initialize
 * (10-20sec). It is not possible to suffer this for every Exchange command
 * that we execute. Therefore we need to initialize PowerShell once, keep
 * the session running and run several commands as needed.
 * </p>
 * <p>
 * For reasons that are perhaps only known to Microsoft the PowerShell does
 * not process commands from stdin. It may not really be a shell, after all.
 * There is a PowerShell Remoting Protocol [MS-PSRP] that might be able to do
 * what we need. But it looks completely nuts. The PSRP seems to be using 
 * rogue element in the WS-MAN messages that contain base64-encoded mix of 
 * binary data and stand-alone XML snippets that contain other base-64 encoded
 * data which we have no idea what they are because we were too scared to have
 * a deeper look. Therefore we have made no attempt to implement PSRP. We value
 * our sanity.
 * </p>
 * <p>
 * Instead, we have to implement a shell inside PowerShell. So we can shell
 * while we shell. The PowerHell is a simple loop that takes string from stdin
 * and executes it. The executions are separated by prompt, so that the client
 * side can determine when a command execution ends and next command can be
 * sent.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellWinRmLoopImpl extends AbstractPowerHellWinRmImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellWinRmLoopImpl.class);
	public static final String PROMPT = ":::P0w3Rh3llPr0mPt:::";
	
	// Configuration
	private String initScriptlet;
	private String prompt = PROMPT;
	
	// State
	private boolean isLoopRunning = false;
	private Command command;
	
	public String getInitScriptlet() {
		return initScriptlet;
	}

	public void setInitScriptlet(String initScriptlet) {
		this.initScriptlet = initScriptlet;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
	
	@Override
	public String getImplementationName() {
		return "WinRM Loop";
	}

	@Override
	public void connect() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		super.connect();
		startMainLoop();
	}
	
	private void startMainLoop() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		String psScript = createScript(initScriptlet);
		LOG.debug("Executing powershell. Main loop script: {}", psScript);
		
		long tsStart = System.currentTimeMillis();
		
		try {
			
			command = getClient().commandAsync(encodePowerShellToString(psScript, null));
			
		} catch (Fault e) {
			processFault("Executing command failed", e);
		}
		
		long tsAfterInit = System.currentTimeMillis();
		LOG.debug("Powershell running. init time: {} ms", tsAfterInit-tsStart);
		
		while (true) {
			Integer exitCode = command.receive();
			
			String out = command.getLastOut();
    		String err = command.getLastErr();
    		logData("O<", out);
    		logData("E<", err);
    		
    		if (out != null && out.contains(prompt)) {
    			LOG.trace("First prompt detected");
    			break;
    		}
    		
    		if (exitCode != null) {
    			LOG.error("Exit code received before first prompt: {}", exitCode);
    			disconnectClient();
    			PowerHellExecutionException e = new PowerHellExecutionException("Exit code received before first prompt", exitCode);
    			e.setStdout(out);
    			e.setStderr(err);
    			throw e;
    		}
    	}
		
		isLoopRunning = true;
	}

	@Override
	public String runCommand(String psScript, Map<String, Object> arguments) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {		
		long tsCommStart = System.currentTimeMillis();
		
		int attempt = 1;
		
		StringWriter writerStdOut = null;
		StringWriter writerStdErr = null;
		String promptMessage = null;
		String outCommandLine = null;
		
		int maxLoopStartAttempts;
		if (isLoopRunning) {
			// First "start" may reuse existing connection.
			// We need another attempt that starts fresh with new connection.
			maxLoopStartAttempts = 2;
		} else {
			// Even the first attempt is create new connection.
			// No re-connect necessary.
			maxLoopStartAttempts = 1;
		}
		
		while (true) {
			
			if (!isLoopRunning) {
				startMainLoop();
			}
			
			writerStdOut = new StringWriter();
			writerStdErr = new StringWriter();
			promptMessage = null;
			
			outCommandLine = encodePowerShellVariablesAndCommandToString(psScript, arguments);
			String tx = outCommandLine + "\r\n" + prompt + "\r\n";
			logData("I>", tx);
			
			try {
				
				command.send(tx);
				
				// success
				break;
				
			} catch (SOAPFaultException e) {
				// We do not get any error that is more specific than SOAPFault.
				// Therefore it is difficult to decide whether to re-start loop and re-try the command
				// (e.g. case of "invalid selectors" after WinRM service restart.
				// Or whether there is no point and just throw the error up.
				// So let's be conservative, try to re-start the loop couple of times
				// and then throw the error up.
				LOG.error("SOAP fault (attempt {}/{}): {}", attempt, maxLoopStartAttempts, e.getMessage(), e);
				
				if (attempt >= maxLoopStartAttempts) {
					throw e;
				}
				attempt++;
				isLoopRunning = false;
				disconnectClient();
				connectClient();
				continue;
				
			} catch (Throwable e) {
				// Avoid any looping for other errors
				throw e;
			}
			
			// not reached
		}
		
		while (true) {
			Integer exitCode = command.receive();
			
			String out = command.getLastOut();
    		String err = command.getLastErr();
    		logData("O<", out);
    		logData("E<", err);

    		if (err != null) {
    			writerStdErr.write(err);
    		}
    		
    		if (out != null) {
    			int indexOfPrompt = out.indexOf(prompt);
    			if (indexOfPrompt >=0 ) {
    				writerStdOut.write(out.substring(0,indexOfPrompt));
    				int indexOfEol = out.indexOf("\n", indexOfPrompt);
    				promptMessage = out.substring(indexOfPrompt+prompt.length(), indexOfEol);
    				LOG.trace("Prompt detected, msg: {}", promptMessage);
    				if (promptMessage != null && !promptMessage.matches("\\s*")) {
    					PowerHellExecutionException e = new PowerHellExecutionException(promptMessage, exitCode);
    	    			e.setStdout(writerStdOut.toString());
    	    			e.setStderr(writerStdErr.toString());
    	    			e.setPromptMessage(promptMessage);
    	    			throw e;
    				}
    				break;
    			} else {
    				writerStdOut.write(out);
    			}
    		}
    		
    		if (exitCode != null) {
    			// Most likely cause is that some script invoked "exit" keyword.
    			if (exitCode == 0) {
    				LOG.debug("Exit code received during command execution: {} (will restart main loop)", exitCode);
    				// Let's assume that this is harmless, like invocation of "exit" with no error code.
    				// However, we need to restart the main loop for next command.
    				isLoopRunning = false;
    				break;
    			} else {
	    			LOG.error("Exit code received during command execution: {}", exitCode);
	    			disconnectClient();
	    			PowerHellExecutionException e = new PowerHellExecutionException("Exit code received during command execution", exitCode);
	    			e.setStdout(writerStdOut.toString());
	    			e.setStderr(writerStdErr.toString());
	    			e.setPromptMessage(promptMessage);
	    			throw e;
    			}
    		}
		}		
		
		logExecution(outCommandLine, tsCommStart);
		
		return writerStdOut.toString();
	}
	
	@Override
	public int disconnect() {
		LOG.debug("Disconnecting, sending exit command");
		
		String tx = prompt + " exit\r\n";
		logData("I>", tx);
		
		command.send(tx);
	
		Integer exitCode = null;
		while (true) {
			exitCode = command.receive();
			
			String out = command.getLastOut();
    		String err = command.getLastErr();
    		logData("O<", out);
    		logData("E<", err);

    		if (exitCode != null) {    			
    			LOG.debug("Powershell exit code: {}", exitCode);
    			break;
    		}
		}
		
		command.release();
		
		super.disconnect();
		
		return exitCode;
	}
	
	private String createScript(String initScriptlet) {
		StringBuilder sb = new StringBuilder();
		if (initScriptlet != null) {
			sb.append(initScriptlet);
			sb.append("\n");
		}
		sb.append("write-host '").append(prompt).append("'\r\n");
		sb.append("while($true) {\r\n");
		sb.append("  $powerhellCommand = ''\r\n");
		sb.append("  while($powerhellLine = [Console]::In.ReadLine()) {\r\n");
		sb.append("    if($powerhellLine -eq \"").append(prompt).append(" exit\") { exit }\r\n");
		sb.append("    if($powerhellLine -eq \"").append(prompt).append("\") { break }\r\n");
		sb.append("    $powerhellCommand = $powerhellCommand + $powerhellLine + \"`n\"\r\n");
		sb.append("  }\r\n");
		sb.append("  Invoke-Expression -ErrorVariable powerhellError $powerhellCommand\r\n");
		sb.append("  write-host '").append(prompt).append("'$powerhellError\r\n");
		sb.append("  $powerhellError = \"\"\r\n");
		sb.append("}\r\n");
		return sb.toString();
	}
	
}
