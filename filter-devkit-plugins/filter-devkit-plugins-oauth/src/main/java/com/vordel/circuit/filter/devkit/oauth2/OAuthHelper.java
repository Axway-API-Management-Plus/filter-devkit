package com.vordel.circuit.filter.devkit.oauth2;

import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContextPlugin;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.oauth2.jaxrs.OAuthException;
import com.vordel.circuit.oauth.kps.ApplicationDetails;

@ExtensionContextPlugin("oauth.helper")
public class OAuthHelper {
	@SubstitutableMethod("OAuthException")
	public static OAuthException createOAuthException(@SelectorExpression("oauth.error") String error, @SelectorExpression("oauth.error_uri") String error_uri, @SelectorExpression("oauth.error_description") String error_description) {
		return error == null ? null : new OAuthException(error, error_uri, error_description);
	}
	
	@SubstitutableMethod("ApplicationDetails")
	public static ApplicationDetails getAppDetailsFromClientId(Message m, @SelectorExpression("oauth.request.client_id") String client_id) {
		return OAuthGuavaCache.getAppDetailsFromClientId(m, client_id);
	}
}
