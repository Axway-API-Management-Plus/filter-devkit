package com.axway.aws.lambda.quick;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.aws.AWSFactory;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.Field;
import com.vordel.mime.Body;
import com.vordel.trace.Trace;

@QuickFilterType(name = "AWSLambdaQuickFilter", resources = "aws_lambda.properties", ui = "aws_lambda.xml")
public class AWSLambdaQuickFilter extends QuickJavaFilterDefinition {
	private final static Selector<String> PAYLOAD_SELECTOR = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, String.class);
	private static final ClientConfiguration DEFAULT_CONFIGURATION = new ClientConfiguration();
	private final static String ASSUME_ROLE_FALSE = "ASSUME_ROLE_FALSE";
	private final static String DEFAULT_SESSIONNAME = "AXWAY-SESSION";
	private final static String DEFAULT_REGION = "eu-west-1";

	private Selector<Integer> awsSessionDuration;
	private Selector<String> awsFunctionNameSelector;
	private Selector<String> awsRoleSessionName;
	private Selector<String> awsExternalId;
	private Selector<String> awsRoleARN;

	private ClientConfiguration awsClientConfiguration;
	private AWSCredentials vFilterCredentials;

	private String vAwsRegion;
	private String authAWSWith;

	private AWSLambdaHolder awsHolder;

	@QuickFilterField(name = "functionName", cardinality = "1", type = "string")
	private void setFunctionName(ConfigContext ctx, Entity entity, String field) {
		awsFunctionNameSelector = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "awsCredential", cardinality = "?", type = "^ApiKeyProfile")
	private void setCredential(ConfigContext ctx, Entity entity, String field) {
		vFilterCredentials = AWSFactory.getCredentials(ctx, entity, field);
	}

	@QuickFilterField(name = "region", cardinality = "1", type = "string", defaults = DEFAULT_REGION)
	private void setRegion(ConfigContext ctx, Entity entity, String field) {
		vAwsRegion = entityStringValue(entity, field);
	}

	@QuickFilterField(name = "authAWSWith", cardinality = "1", type = "string")
	private void setAuthWith(ConfigContext ctx, Entity entity, String field) {
		authAWSWith = entityStringValue(entity, field);
	}

	@QuickFilterField(name = "awsRoleARN", cardinality = "?", type = "string")
	private void setRoleARN(ConfigContext ctx, Entity entity, String field) {
		awsRoleARN = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "awsRoleSessionName", cardinality = "1", type = "string", defaults = DEFAULT_SESSIONNAME)
	private void setRoleSessionName(ConfigContext ctx, Entity entity, String field) {
		awsRoleSessionName = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "awsExternalId", cardinality = "?", type = "string")
	private void setExternalId(ConfigContext ctx, Entity entity, String field) {
		awsExternalId = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "awsSessionDuration", cardinality = "?", type = "integer")
	private void setSessionDuration(ConfigContext ctx, Entity entity, String field) {
		awsSessionDuration = new Selector<Integer>(entity.getStringValue("awsSessionDuration"), Integer.class);
	}

	@QuickFilterField(name = "clientConfiguration", cardinality = "?", type = "^AWSClientConfiguration")
	private void setClientConfiguration(ConfigContext ctx, Entity entity, String field) {
		/* allow use of selectors for client configuration reference */
		ESPK configPK = entity.getReferenceValue(field);
		DelayedESPK delayedPK = configPK instanceof DelayedESPK ? (DelayedESPK) configPK : new DelayedESPK(configPK);
		Entity configEntity = ctx.getEntity(configPK = delayedPK.substitute(Dictionary.empty));

		awsClientConfiguration = createClientConfiguration(ctx, configEntity);
	}

	public static ClientConfiguration createClientConfiguration(ConfigContext ctx, Entity entity) throws EntityStoreException {
		/*
		 * XXX this method is almost a cut'n'paste of
		 * AWSFactory.createClientConfiguration(). It adds the abillty to use selectors
		 * within configuration values.
		 */
		if (entity == null) {
			Trace.debug("using empty default ClientConfiguration");
			return DEFAULT_CONFIGURATION;
		} else {
			ClientConfiguration clientConfig = new ClientConfiguration();

			/* allow use of selectors for client configuration items */

			if (entityContainsKey(entity, "connectionTimeout")) {
				clientConfig.setConnectionTimeout(entityIntegerValue(entity, "connectionTimeout"));
			}

			if (entityContainsKey(entity, "maxConnections")) {
				clientConfig.setMaxConnections(entityIntegerValue(entity, "maxConnections"));
			}

			if (entityContainsKey(entity, "maxErrorRetry")) {
				clientConfig.setMaxErrorRetry(entityIntegerValue(entity, "maxErrorRetry"));
			}

			if (entityContainsKey(entity, "protocol")) {
				clientConfig.setProtocol(Protocol.valueOf(entity.getStringValue("protocol")));
			}

			if (entityContainsKey(entity, "proxyDomain")) {
				clientConfig.setProxyDomain(entity.getStringValue("proxyDomain"));
			}

			if (entityContainsKey(entity, "proxyHost")) {
				clientConfig.setProxyHost(entity.getStringValue("proxyHost"));
			}

			if (entityContainsKey(entity, "proxyPassword")) {
				try {
					byte[] password = entity.getEncryptedValue("proxyPassword");
					String proxyPassword = new String(ctx.getCipher().decrypt(password));
					clientConfig.setProxyPassword(proxyPassword);
				} catch (GeneralSecurityException var5) {
					Trace.error(var5);
				}
			}

			if (entityContainsKey(entity, "proxyPort")) {
				clientConfig.setProxyPort(entityIntegerValue(entity, "proxyPort"));
			}

			if (entityContainsKey(entity, "proxyUsername")) {
				clientConfig.setProxyUsername(entityStringValue(entity, "proxyUsername"));
			}

			if (entityContainsKey(entity, "proxyWorkstation")) {
				clientConfig.setProxyWorkstation(entityStringValue(entity, "proxyWorkstation"));
			}

			if (entityContainsKey(entity, "socketTimeout")) {
				clientConfig.setSocketTimeout(entityIntegerValue(entity, "socketTimeout"));
			}

			if (entityContainsKey(entity, "userAgent")) {
				/*
				 * original setUserAgent() method is deprecated using setUserAgentPrefix()
				 * instead
				 */
				clientConfig.setUserAgentPrefix(entityStringValue(entity, "userAgent"));
			}

			if (entityContainsKey(entity, "socketSendBufferSizeHint") && entityContainsKey(entity, "socketReceiveBufferSizeHint")) {
				clientConfig.setSocketBufferSizeHints(entityIntegerValue(entity, "socketSendBufferSizeHint"), entityIntegerValue(entity, "socketReceiveBufferSizeHint"));
			}

			return clientConfig;
		}
	}

	private static int entityIntegerValue(Entity entity, String fieldName) {
		return new Selector<Integer>(entity.getStringValue(fieldName), Integer.class).substitute(Dictionary.empty);
	}

	private static String entityStringValue(Entity entity, String fieldName) {
		return new Selector<String>(entity.getStringValue(fieldName), String.class).substitute(Dictionary.empty);
	}

	private static boolean entityContainsKey(Entity entity, String fieldName) {
		if (!entity.containsKey(fieldName)) {
			return false;
		} else {
			Field field = entity.getField(fieldName);

			if (field.isRefType()) {
				ESPK resolvedPK = EntityStore.ES_NULL_PK;
				ESPK delegatedPK = field.getReference();

				if (delegatedPK != null) {
					DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK : new DelayedESPK(delegatedPK);

					resolvedPK = delayedPK.substitute(Dictionary.empty);
				}

				return !EntityStore.ES_NULL_PK.equals(resolvedPK);
			} else {
				String value = entity.getStringValue(fieldName);

				return value != null && value.length() != 0;
			}
		}
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		if (awsClientConfiguration == null) {
			/* create an empty config */
			awsClientConfiguration = createClientConfiguration(ctx, null);
		}

		if (vAwsRegion == null) {
			vAwsRegion = DEFAULT_REGION;
		}

		if (ASSUME_ROLE_FALSE.equals(authAWSWith)) {
			awsHolder = new AWSLambdaHolder(awsClientConfiguration, vAwsRegion);

			awsHolder.setCredentials(vFilterCredentials);
		} else {
			awsHolder = new AWSAssumeRoleLambdaHolder(awsClientConfiguration, vAwsRegion, awsRoleARN, awsRoleSessionName, awsExternalId, awsSessionDuration);
		}
	}

	public boolean isAssumeRole() {
		return !ASSUME_ROLE_FALSE.equals(authAWSWith);
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		String vLambdaFunctionName = awsFunctionNameSelector.substitute(m);
		boolean response = false;

		if (vLambdaFunctionName == null) {
			throw new CircuitAbortException("no value for functionName");
		}

		InvokeRequest invokeRequest = new InvokeRequest();
		Body body = (Body) m.get(MessageProperties.CONTENT_BODY);
		byte[] payload = ScriptHelper.toByteArray(body);

		invokeRequest.setFunctionName(vLambdaFunctionName);
		invokeRequest.addHandlerContext(AWSVordelRequestHandler.VORDEL_MESSAGE, m);
		invokeRequest.addHandlerContext(AWSVordelRequestHandler.SENT_PAYLOAD, payload);
		invokeRequest.addHandlerContext(AWSVordelRequestHandler.CONTENT_TYPE, body.getHeaders().getHeader("Content-Type"));
		invokeRequest.setPayload(ByteBuffer.wrap(payload));

		/* clean up message before starting */
		m.remove(MessageProperties.HTTP_RSP_STATUS);
		m.remove(MessageProperties.HTTP_RSP_INFO);
		m.remove(MessageProperties.HTTP_HEADERS);
		m.remove(MessageProperties.CONTENT_BODY);

		Trace.debug("Calling function : " + vLambdaFunctionName);

		try {
			AWSLambda awsLambda = awsHolder.updateAwsLambda(m).getAwsLambda();
			InvokeResult invokeResult = null;

			if (awsLambda != null) {
				long startTime = System.currentTimeMillis();

				invokeRequest.addHandlerContext(AWSVordelRequestHandler.FILTER_START, startTime);
				invokeResult = awsLambda.invoke(invokeRequest);

				long elapsedTime = System.currentTimeMillis() - startTime;

				m.put("http.response.time", Long.toString(elapsedTime) + "ms");

				int statusCode = invokeResult.getStatusCode();
				Map<String, String> lambdaResponseHeaders = invokeResult.getSdkHttpMetadata().getHttpHeaders();

				m.put("aws.lambda.http.status.code", statusCode);
				m.put("aws.lambda.http.headers", lambdaResponseHeaders);
				m.put("aws.lambda.response", PAYLOAD_SELECTOR.substitute(m));
				// If the response contains a function error
				if (StringUtils.isNotBlank(invokeResult.getFunctionError())) {
					Trace.error("Error returned by Lambda function : " + vLambdaFunctionName);

					m.put("aws.lambda.function.error", invokeResult.getFunctionError());
				} else {
					response = true;
				}
			} else {
				Trace.error("awsLambda IS NULL");
			}
		} catch (RuntimeException e) {
			Trace.error("Exception while invoking Lambda function", e);
		}

		return response;
	}

	@Override
	public void detachFilter() {
	}
}
