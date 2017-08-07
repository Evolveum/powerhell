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
import org.apache.http.impl.auth.SSLEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.Command;
import io.cloudsoft.winrm4j.client.WinRmClient;

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
public class PowerHell {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHell.class);
	public static final String PROMPT = ":::P0w3Rh3llPr0mPt:::";
	
	// Configuration
	private String endpointUrl;
	private String authenticationScheme;
	private String domainName;
	private String userName;
	private String password;
	private HostnameVerifier hostnameVerifier;
	private SSLEngineFactory sslEngineFactory;
	private boolean disableCertificateChecks;
	private String initScriptlet;
	private String prompt = PROMPT;
	private boolean isLoopRunning = false;
	
	// State
	private WinRmClient client;
	private Command command;
	
	public String getEndpointUrl() {
		return endpointUrl;
	}

	public void setEndpointUrl(String endpointUrl) {
		this.endpointUrl = endpointUrl;
	}

	public String getAuthenticationScheme() {
		return authenticationScheme;
	}

	public void setAuthenticationScheme(String authenticationScheme) {
		this.authenticationScheme = authenticationScheme;
	}

	public String getDomainName() {
		return domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public HostnameVerifier getHostnameVerifier() {
		return hostnameVerifier;
	}

	public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
		this.hostnameVerifier = hostnameVerifier;
	}

	public SSLEngineFactory getSslEngineFactory() {
		return sslEngineFactory;
	}

	public void setSslEngineFactory(SSLEngineFactory sslEngineFactory) {
		this.sslEngineFactory = sslEngineFactory;
	}

	public boolean isDisableCertificateChecks() {
		return disableCertificateChecks;
	}

	public void setDisableCertificateChecks(boolean disableCertificateChecks) {
		this.disableCertificateChecks = disableCertificateChecks;
	}

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

	public void connect() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		WinRmClient.Builder builder = WinRmClient.builder(endpointUrl, authenticationScheme);
		builder.credentials(domainName, userName, password);
		builder.disableCertificateChecks(disableCertificateChecks);
		builder.hostnameVerifier(hostnameVerifier);
		builder.sslEngineFactory(sslEngineFactory);
		
		LOG.debug("Connecting WinRM for PowerHell. Endpoint: {0}", endpointUrl);
		client = builder.build();
		
		startMainLoop();
	}
	
	private void startMainLoop() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		String psScript = createScript(initScriptlet);
		LOG.debug("Executing powershell. Main loop script: {0}", psScript);
		
		long tsStart = System.currentTimeMillis();
		
		try {
			
			command = client.commandAsync("powershell -EncodedCommand "+encodeCommand(psScript));
			
		} catch (Fault e) {
			processFault("Executing command failed", e);
		}
		
		long tsAfterInit = System.currentTimeMillis();
		LOG.debug("Powershell running. init time: {0} ms", tsAfterInit-tsStart);
		
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
    			LOG.error("Exit code received before first prompt: {0}", exitCode);
    			client.disconnect();
    			PowerHellExecutionException e = new PowerHellExecutionException("Exit code received before first prompt", exitCode);
    			e.setStdout(out);
    			e.setStderr(err);
    			throw e;
    		}
    	}
		
		isLoopRunning = true;
	}

	private void processFault(String message, Fault e) throws PowerHellSecurityException, PowerHellCommunicationException {
		// Fault does not have useful information on its own. Try to mine out something useful.
		Throwable cause = e.getCause();
		if (cause instanceof IOException) {
			if (cause.getMessage() != null && cause.getMessage().contains("Authorization loop detected")) {
				throw new PowerHellSecurityException(cause.getMessage(), e);
			}
		}
		throw new PowerHellCommunicationException(message + ": " + e.getMessage(), e);
	}

	public String runCommand(String outCommandLine) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		
		long tsCommStart = System.currentTimeMillis();
		
		if (!isLoopRunning) {
			startMainLoop();
		}
		
		StringWriter writerStdOut = new StringWriter();
		StringWriter writerStdErr = new StringWriter();
		String promptMessage = null;
		
		String tx = outCommandLine + "\r\n" + prompt + "\r\n";
		logData("I>", tx);
		
		command.send(tx);
		
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
    				LOG.trace("Prompt detected, msg: {0}", promptMessage);
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
    				LOG.debug("Exit code received during command execution: {0} (will restart main loop)", exitCode);
    				// Let's assume that this is harmless, like invocation of "exit" with no error code.
    				// However, we need to restart the main loop for next command.
    				isLoopRunning = false;
    				break;
    			} else {
	    			LOG.error("Exit code received during command execution: {0}", exitCode);
	    			client.disconnect();
	    			PowerHellExecutionException e = new PowerHellExecutionException("Exit code received during command execution", exitCode);
	    			e.setStdout(writerStdOut.toString());
	    			e.setStderr(writerStdErr.toString());
	    			e.setPromptMessage(promptMessage);
	    			throw e;
    			}
    		}
		}		
		
		long tsCommStop = System.currentTimeMillis();
		
		LOG.debug("Command {0} run time: {1} ms", outCommandLine, tsCommStop-tsCommStart);
		
		return writerStdOut.toString();
	}
	
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
    			LOG.debug("Powershell exit code: {0}", exitCode);
    			break;
    		}
		}
		
		command.release();
		client.disconnect();
		
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
	
	private String encodeCommand(String command) {
		byte[] bytes = command.getBytes(Charset.forName("UTF-16LE"));
        return DatatypeConverter.printBase64Binary(bytes);
	}
	
	private void logData(String prefix, String data) {
		if (LOG.isTraceEnabled()) {
			if (data != null && !data.isEmpty()) {
				LOG.trace("{0} {1}", prefix, data);
			}
		}
	}

}
