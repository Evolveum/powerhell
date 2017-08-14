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
public class PowerHellLocalExecWinRsPowerShellImpl extends PowerHellLocalExecWinRsImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellLocalExecWinRsPowerShellImpl.class);

	@Override
	protected String getImplementationName() {
		return "Local winrs PowerShell Execution";
	}
	
	@Override
	protected String encodeRemoteCommand(String command, Map<String,Object> arguments) {
		return encodePowerShellToString(command, arguments);
	}
	
}
