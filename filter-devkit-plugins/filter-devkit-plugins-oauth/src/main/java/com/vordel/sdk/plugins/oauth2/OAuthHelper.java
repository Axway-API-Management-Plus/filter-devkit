package com.vordel.sdk.plugins.oauth2;

import com.vordel.circuit.Message;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.script.bind.ExtensionPlugin;
import com.vordel.circuit.script.bind.SelectorExpression;
import com.vordel.circuit.script.bind.SubstitutableMethod;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthException;

@ExtensionPlugin("oauth.helper")
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
