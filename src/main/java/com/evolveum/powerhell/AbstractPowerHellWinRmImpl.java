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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.Command;
import io.cloudsoft.winrm4j.client.WinRmClient;

/**
 * <p>
 * TODO
 * </p> 
 * 
 * @author semancik
 */
public abstract class AbstractPowerHellWinRmImpl extends AbstractPowerHellImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractPowerHellWinRmImpl.class);
	
	// Configuration
	private String endpointUrl;
	private String authenticationScheme;
	private String domainName;
	private String userName;
	private String password;
	private HostnameVerifier hostnameVerifier;
	private boolean disableCertificateChecks;
	
	// State
	private WinRmClient client;
	
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

	public boolean isDisableCertificateChecks() {
		return disableCertificateChecks;
	}

	public void setDisableCertificateChecks(boolean disableCertificateChecks) {
		this.disableCertificateChecks = disableCertificateChecks;
	}

	@Override
	public void connect() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		connectClient();
	}
	
	protected boolean isClientConnected() {
		return client != null;
	}
	
	protected void connectClient() throws PowerHellExecutionException, PowerHellSecurityException, PowerHellCommunicationException {
		WinRmClient.Builder builder = WinRmClient.builder(endpointUrl, authenticationScheme);
		builder.credentials(domainName, userName, password);
		builder.disableCertificateChecks(disableCertificateChecks);
		builder.hostnameVerifier(hostnameVerifier);
		builder.retriesForConnectionFailures(1);
		
		LOG.debug("Connecting WinRM for PowerHell {} Endpoint: {}", getImplementationName(), endpointUrl);
		client = builder.build();
	}
	
	protected WinRmClient getClient() {
		return client;
	}
	
	protected void disconnectClient() {
		if (client != null) {
			client.disconnect();
			client = null;
		}
	}

	protected void processFault(String message, Fault e) throws PowerHellSecurityException, PowerHellCommunicationException {
		// Fault does not have useful information on its own. Try to mine out something useful.
		Throwable cause = e.getCause();
		if (cause instanceof IOException) {
			if (cause.getMessage() != null && cause.getMessage().contains("Authorization loop detected")) {
				throw new PowerHellSecurityException(cause.getMessage(), e);
			}
		}
		throw new PowerHellCommunicationException(message + ": " + e.getMessage(), e);
	}

	
	@Override
	public int disconnect() {
		if (client != null) {
			client.disconnect();
		}
		return 0;
	}

}
