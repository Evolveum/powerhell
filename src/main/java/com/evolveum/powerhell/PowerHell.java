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

public interface PowerHell {
	
	/**
	 * Initialize PowerHell.
	 * Connects to server, starts the master process, etc.
	 */
	void connect() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException;
	
	String runCommand(String outCommandLine) throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException;

	/**
	 * Disposes PowerHell.
	 * Disconnects from server, ends the master process, etc.
	 * @return exit code
	 */
	int disconnect();
}
