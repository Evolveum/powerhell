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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * PowerHell implementation that executes the commands by using plain WinRM, wrapping them to powershell.exe.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellWinRmExecPowerShellImpl extends PowerHellWinRmExecImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellWinRmExecPowerShellImpl.class);
	
	@Override
	protected String getImplementationName() {
		return "WinRM PowerShell Execution";
	}
	
	protected String encodeCommand(String outCommandLine) {
		return encodePowerShell(outCommandLine);
	}
	
}
