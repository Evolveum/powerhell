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
 * PowerHell implementation that executes the commands by using plain WinRM, wrapping them in powershell.
 * </p> 
 * 
 * @author semancik
 */
public class PowerHellLocalExecPowerShellImpl extends PowerHellLocalExecImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(PowerHellLocalExecPowerShellImpl.class);
	
	@Override
	public String getImplementationName() {
		return "Local PowerShell Execution";
	}
	
	// We need to wrap execution in powershell command-line.
	@Override
	protected List<String> encodeCommand(String command, Map<String, Object> arguments) {
		return encodePowerShellToList(command, arguments);
	}
}
