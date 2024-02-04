package com.vordel.circuit.filter.devkit.aws.lambda;

import java.time.Instant;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

public class AWSAssumeRoleLambdaHolder extends AWSLambdaHolder {
	private final Selector<String> sAwsRoleARN;
	private final Selector<String> sAwsRoleSessionName;
	private final Selector<String> sAwsExternalId;
	private final Selector<Integer> sAwsSessionDuration;

	private Credentials awsCredentials;
	private String awsRoleArn;
	private String awsRoleSessionName;
	private String awsExternalId;
	private int awsSessionDuration;

	protected AWSAssumeRoleLambdaHolder(ClientConfiguration clientConfiguration, String region, Selector<String> sAwsRoleARN, Selector<String> sAwsRoleSessionName, Selector<String> sAwsExternalId, Selector<Integer> sAwsSessionDuration) {
		super(clientConfiguration, region);

		this.sAwsRoleARN = sAwsRoleARN;
		this.sAwsRoleSessionName = sAwsRoleSessionName;
		this.sAwsExternalId = sAwsExternalId;
		this.sAwsSessionDuration = sAwsSessionDuration;

	}

	private static <T> boolean dirty(T o1, T o2) {
		return !(o1 == null ? o2 == null : o1.equals(o2));
	}

	private void setCredentials(String awsRoleArn, String awsRoleSessionName, String awsExternalId, int awsSessionDuration) {
		synchronized (sync) {
			/* initialize sts session */
			initializeStsSession(awsRoleArn, awsRoleSessionName, awsExternalId, awsSessionDuration);

			/* create credentials */
			BasicSessionCredentials basicSessionCredentials = new BasicSessionCredentials(this.awsCredentials.getAccessKeyId(), this.awsCredentials.getSecretAccessKey(), this.awsCredentials.getSessionToken());

			/* and use credentials for lambda */
			setCredentials(basicSessionCredentials);
		}
	}

	private void initializeStsSession(String pRoleARN, String pRoleSessionName, String pAwsExternalId, int pAwsSessionDuration) {
		synchronized (sync) {
			AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard().withCredentials(new InstanceProfileCredentialsProvider(false)).withRegion(region).build();

			Trace.debug("roleARN          : " + pRoleARN);
			Trace.debug("roleSessionName  : " + pRoleSessionName);

			AssumeRoleRequest vAssumeRoleRequest = new AssumeRoleRequest().withRoleArn(pRoleARN).withRoleSessionName(pRoleSessionName);
			// Add externalId if provided
			if (StringUtils.isNotBlank(pAwsExternalId)) {
				Trace.debug("Adding external ID : " + pAwsExternalId);
				vAssumeRoleRequest.withExternalId(pAwsExternalId);
			}
			// Specify the session duration if needed
			if (pAwsSessionDuration >= 900) {
				// minimum is 900
				Trace.debug("Adding session duration : " + pAwsSessionDuration);
				vAssumeRoleRequest.withDurationSeconds(pAwsSessionDuration);
			} else {
				pAwsSessionDuration = 3600;

				Trace.error("Minimum session duration is 900, default value will be used (3600). Provided value is : " + pAwsSessionDuration);
			}
			Trace.debug("vAssumeRoleRequest - getRoleArn : " + vAssumeRoleRequest.getRoleArn());
			Trace.debug("vAssumeRoleRequest - getRoleSessionName : " + vAssumeRoleRequest.getRoleSessionName());
			Trace.debug("vAssumeRoleRequest - getDurationSeconds : " + vAssumeRoleRequest.getDurationSeconds());
			AssumeRoleResult assumeRoleResult = sts.assumeRole(vAssumeRoleRequest);

			Trace.info("STS session initialized");

			this.awsRoleArn = pRoleARN;
			this.awsRoleSessionName = pRoleSessionName;
			this.awsExternalId = pAwsExternalId;
			this.awsSessionDuration = pAwsSessionDuration;
			this.awsCredentials = assumeRoleResult.getCredentials();
		}
	}

	public boolean isStsCredentialsExpired() {
		boolean response = false;

		synchronized (sync) {
			if (awsCredentials != null) {
				Instant now = Instant.now();
				Instant stsCredentialsExpirationDate = awsCredentials.getExpiration().toInstant();

				response = now.isAfter(stsCredentialsExpirationDate);
			} else {
				Trace.error("STS credentials not instanciated");
			}
		}

		return response;
	}

	public void updateAwsLambda(String awsRoleArn, String awsRoleSessionName, String awsExternalId, int awsSessionDuration) {
		synchronized (sync) {
			if (awsCredentials == null) {
				/* settings have changed... update credentials */
				setCredentials(awsRoleArn, awsRoleSessionName, awsExternalId, awsSessionDuration);
			} else if (isStsCredentialsExpired()) {
				/* settings have expired... update credentials */
				setCredentials(awsRoleArn, awsRoleSessionName, awsExternalId, awsSessionDuration);
			} else if (dirty(awsRoleArn, this.awsRoleArn) || dirty(awsRoleSessionName, this.awsRoleSessionName) || dirty(awsExternalId, this.awsExternalId) || (awsSessionDuration != this.awsSessionDuration)) {
				/* settings have changed... update credentials */
				setCredentials(awsRoleArn, awsRoleSessionName, awsExternalId, awsSessionDuration);
			}
		}
	}

	/**
	 * Calculate AWS session duration for assumeRole. Minimum value is 900s.
	 * 
	 * @param vAwsSessionDuration
	 * @return
	 */
	private static Integer calculateAwsSessionDuration(Integer vAwsSessionDuration) {
		if (vAwsSessionDuration == null) {
			vAwsSessionDuration = 3600;
		} else {
			vAwsSessionDuration = Math.max(vAwsSessionDuration, 900);
			vAwsSessionDuration = Math.min(vAwsSessionDuration, 86400);
		}
		return vAwsSessionDuration;
	}

	@Override
	public AWSLambdaHolder updateAwsLambda(Message m) throws CircuitAbortException {
		// Lambda call using assume role feature
		// Get values from incoming message
		String vAwsRoleARN = sAwsRoleARN == null ? null : sAwsRoleARN.substitute(m);
		String vAwsRoleSessionName = sAwsRoleSessionName == null ? null : sAwsRoleSessionName.substitute(m);
		String vAwsExternalId = sAwsExternalId == null ? null : sAwsExternalId.substitute(m);
		Integer vAwsSessionDuration = sAwsSessionDuration == null ? 3600 : calculateAwsSessionDuration(sAwsSessionDuration.substitute(m));
		Trace.debug("vAwsSessionDuration : " + vAwsSessionDuration);

		if (vAwsRoleARN == null) {
			throw new CircuitAbortException("no value for awsRoleARN");
		}
		if (vAwsRoleSessionName == null) {
			throw new CircuitAbortException("no value for awsRoleSessionName");
		}
		if (vAwsExternalId == null) {
			throw new CircuitAbortException("no value for awsExternalId");
		}

		Trace.debug("vAwsRoleARN from message : " + vAwsRoleARN);
		Trace.debug("vAwsRoleSessionName from message : " + vAwsRoleSessionName);
		Trace.debug("vAwsExternalId from message : " + vAwsExternalId);
		Trace.debug("vAwsSessionDurationFromFilter from message : " + vAwsSessionDuration);

		updateAwsLambda(vAwsRoleARN, vAwsRoleSessionName, vAwsExternalId, vAwsSessionDuration);

		return this;
	}

}
