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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * PowerHell implementation that executes the commands by using plain WinRM, wrapping them in powershell.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellLocalExecWinRsImpl extends PowerHellLocalExecImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellLocalExecWinRsImpl.class);
	private static final String WINRS_COMMAND = "winrs";
	private static final String WINRS_R_PARAM_PREFIX = "-r:";
	private static final String WINRS_U_PARAM_PREFIX = "-u:";
	private static final String WINRS_P_PARAM_PREFIX = "-p:";
	private static final String WINRS_AD_PARAM = "-ad";
	
	private String endpointUrl;
	private boolean allowDelegate;
	private String userName;
	private String password;
	
	public String getEndpointUrl() {
		return endpointUrl;
	}

	public void setEndpointUrl(String endpointUrl) {
		this.endpointUrl = endpointUrl;
	}

	public boolean isAllowDelegate() {
		return allowDelegate;
	}

	public void setAllowDelegate(boolean allowDelegate) {
		this.allowDelegate = allowDelegate;
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

	@Override
	protected String getImplementationName() {
		return "Local winrs Execution";
	}
	
	// We need to wrap execution in powershell command-line.
	@Override
	protected List<String> encodeCommand(String command, Map<String, Object> arguments) {
		return encodeWinRsToList(command, arguments);
	}
	
	protected List<String> encodeWinRsToList(String command, Map<String,Object> arguments) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add(WINRS_COMMAND);
		commandLine.add(WINRS_R_PARAM_PREFIX + endpointUrl);
		commandLine.add(WINRS_U_PARAM_PREFIX + userName);
		commandLine.add(WINRS_P_PARAM_PREFIX + password);
		if (allowDelegate) {
			commandLine.add(WINRS_AD_PARAM);
		}
		String encodedCommand = encodeCommandExecToString(command, arguments);
		commandLine.add(encodedCommand);
		return commandLine;
	}
	
	protected String encodeRemoteCommand(String command, Map<String,Object> arguments) {
		return encodeCommandExecToString(command, arguments);
	}
	
}
