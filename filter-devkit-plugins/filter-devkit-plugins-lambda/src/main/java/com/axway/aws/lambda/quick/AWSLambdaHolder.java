package com.axway.aws.lambda.quick;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;

public class AWSLambdaHolder {
	protected final Object sync = new Object();

	protected final ClientConfiguration clientConfiguration;
	protected final String region;

	private AWSLambda awsLambda;

	protected AWSLambdaHolder(ClientConfiguration clientConfiguration, String region) {
		this.clientConfiguration = clientConfiguration;
		this.region = region;
	}

	public final AWSLambda getAwsLambda() {
		synchronized (sync) {
			return awsLambda;
		}
	}

	public final void setCredentials(AWSCredentials credentials) {
		if (credentials != null) {
			synchronized (sync) {
				AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard().withClientConfiguration(clientConfiguration).withRegion(Regions.fromName(region)).withCredentials(new AWSStaticCredentialsProvider(credentials));
				List<RequestHandler2> handlers = new ArrayList<RequestHandler2>();

				if (builder.getRequestHandlers() != null) {
					handlers.addAll(builder.getRequestHandlers());
				}
				
				handlers.add(new AWSVordelRequestHandler());

				awsLambda = builder.withRequestHandlers(handlers.toArray(new RequestHandler2[0])).build();
			}
		}
	}

	public AWSLambdaHolder updateAwsLambda(Message m) throws CircuitAbortException {
		return this;
	}
}
