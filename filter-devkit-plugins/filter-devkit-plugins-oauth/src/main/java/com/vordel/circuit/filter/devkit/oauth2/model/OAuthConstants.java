package com.vordel.circuit.filter.devkit.oauth2.model;

/**
 * OAuth constants from IANA
 * 
 * @author rdesaintleger@axway.com
 */
public interface OAuthConstants {
	public static final String response_type_none = "none";
	public static final String response_type_code = "code";
	public static final String response_type_id_token = "id_token";
	public static final String response_type_token = "token";
	public static final String response_type_code_id_token_token = ResponseTypeSet.asString(ResponseTypeSet.asList(String.format("%s %s %s", response_type_code, response_type_id_token, response_type_token)).iterator());
	public static final String response_type_code_id_token = ResponseTypeSet.asString(ResponseTypeSet.asList(String.format("%s %s", response_type_code, response_type_id_token)).iterator());
	public static final String response_type_code_token = ResponseTypeSet.asString(ResponseTypeSet.asList(String.format("%s %s", response_type_code, response_type_token)).iterator());
	public static final String response_type_id_token_token = ResponseTypeSet.asString(ResponseTypeSet.asList(String.format("%s %s", response_type_id_token, response_type_token)).iterator());

	public static final String err_rfc6749_unauthorized_client = "unauthorized_client";
	public static final String err_rfc6749_access_denied = "access_denied";
	public static final String err_rfc6749_unsupported_response_type = "unsupported_response_type";
	public static final String err_rfc6749_invalid_scope = "invalid_scope";
	public static final String err_rfc6749_server_error = "server_error";
	public static final String err_rfc6749_temporarily_unavailable = "temporarily_unavailable";
	public static final String err_rfc6749_invalid_client = "invalid_client";
	public static final String err_rfc6749_invalid_grant = "invalid_grant";
	public static final String err_rfc6749_unsupported_grant_type = "unsupported_grant_type";

	public static final String err_invalid_request = "invalid_request";
	public static final String err_invalid_token = "invalid_token";
	public static final String err_insufficient_scope = "insufficient_scope";
	public static final String err_unsupported_token_type = "unsupported_token_type";
	public static final String err_interaction_required = "interaction_required";
	public static final String err_login_required = "login_required";
	public static final String err_session_selection_required = "session_selection_required";
	public static final String err_consent_required = "consent_required";
	public static final String err_invalid_request_uri = "invalid_request_uri";
	public static final String err_invalid_request_object = "invalid_request_object";
	public static final String err_request_not_supported = "request_not_supported";
	public static final String err_request_uri_not_supported = "request_uri_not_supported";
	public static final String err_registration_not_supported = "registration_not_supported";
	public static final String err_need_info = "need_info";
	public static final String err_request_denied = "request_denied";
	public static final String err_request_submitted = "request_submitted";

	public static final String param_openid_response_mode = "response_mode";

	/**
	 * client identifier issued to the client during the registration process. A
	 * client MAY use the "client_id" request parameter to identify itself when
	 * sending requests to the token endpoint (public client which are not able to
	 * keep its credentials secret).
	 */
	public static final String param_client_id = "client_id";
	/**
	 * client secret issued to the client during the registration process. The
	 * client MAY omit the parameter if the client secret is an empty string.
	 */
	public static final String param_client_secret = "client_secret";
	/**
	 * client desired grant type for the authorization endpoint
	 */
	public static final String param_response_type = "response_type";
	/**
	 * client's redirection endpoint previously established with the authorization
	 * server during the client registration process
	 */
	public static final String param_redirect_uri = "redirect_uri";
	/**
	 * requested access token scope
	 */
	public static final String param_scope = "scope";
	/**
	 * opaque value used by the client to maintain state between the request and
	 * callback
	 */
	public static final String param_state = "state";
	/**
	 * authorization code parameter
	 */
	public static final String param_code = "code";
	/**
	 * oauth error code
	 */
	public static final String param_error = "error";
	/**
	 * oauth error description
	 */
	public static final String param_error_description = "error_description";
	/**
	 * oauth error uri
	 */
	public static final String param_error_uri = "error_uri";
	/**
	 * client grant to obtain access token.
	 */
	public static final String param_grant_type = "grant_type";
	public static final String param_access_token = "access_token";
	public static final String param_token_type = "token_type";
	public static final String param_expires_in = "expires_in";
	/**
	 * user identifier for resource owner password flow
	 */
	public static final String param_username = "username";
	/**
	 * user password for resource owner password flow
	 */
	public static final String param_password = "password";
	/**
	 * client refresh token to refresh an access token
	 */
	public static final String param_refresh_token = "refresh_token";
	/**
	 * OpenID connect nonce parameter
	 */
	public static final String param_nonce = "nonce";
	/**
	 * OpenID connect display parameter
	 */
	public static final String param_display = "display";
	/**
	 * OpenID connect prompt parameter
	 */
	public static final String param_prompt = "prompt";
	/**
	 * OpenID connect maximum authentication age parameter
	 */
	public static final String param_max_age = "max_age";
	/**
	 * End-User's preferred languages and scripts for the user interface
	 */
	public static final String param_ui_locales = "ui_locales";
	/**
	 * End-User's preferred languages and scripts for Claims being returned
	 */
	public static final String param_claims_locales = "claims_locales";
	/**
	 * ID Token previously issued by the Authorization Server being passed as a hint
	 * about the End-User's current or past authenticated session with the Client
	 */
	public static final String param_id_token_hint = "id_token_hint";
	/**
	 * Hint to the Authorization Server about the login identifier the End-User
	 * might use to log in
	 */
	public static final String param_login_hint = "login_hint";
	public static final String param_acr_values = "acr_values";
	/**
	 * requests that specific Claims be returned from the UserInfo Endpoint and/or
	 * in the ID Token.
	 */
	public static final String param_claims = "claims";
	public static final String param_registration = "registration";
	public static final String param_request = "request";
	public static final String param_request_uri = "request_uri";
	public static final String param_id_token = "id_token";
	public static final String param_session_state = "session_state";
	public static final String param_assertion = "assertion";
	public static final String param_client_assertion = "client_assertion";
	public static final String param_client_assertion_type = "client_assertion_type";
	public static final String param_code_verifier = "code_verifier";
	public static final String param_code_challenge = "code_challenge";
	public static final String param_code_challenge_method = "code_challenge_method";
	public static final String param_claim_token = "claim_token";
	public static final String param_pct = "pct";
	public static final String param_rpt = "rpt";
	public static final String param_ticket = "ticket";
	public static final String param_upgraded = "upgraded";
	public static final String param_vtr = "vtr";
	public static final String param_device_code = "device_code";
	public static final String param_resource = "resource";
	public static final String param_audience = "audience";
	public static final String param_requested_token_type = "requested_token_type";
	public static final String param_subject_token = "subject_token";
	public static final String param_subject_token_type = "subject_token_type";
	public static final String param_actor_token = "actor_token";
	public static final String param_actor_token_type = "actor_token_type";
	public static final String param_issued_token_type = "issued_token_type";
	public static final String param_nfv_token = "nfv_token";

	public static final String param_response_mode = "response_mode";

	public static final String type_access_token = "access_token";
	public static final String type_refresh_token = "refresh_token";
	public static final String type_pct = "pct";

	public static final String uri_user_jwt_bearer = "urn:ietf:params:oauth:grant-type:jwt-bearer";
	public static final String uri_client_jwt_bearer = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
	public static final String uri_user_saml2_bearer = "urn:ietf:params:oauth:grant-type:saml2-bearer";
	public static final String uri_client_saml2_bearer = "urn:ietf:params:oauth:client-assertion-type:saml2-bearer";
	
	public static final String uri_token_type_access_token = "urn:ietf:params:oauth:token-type:access_token";
	public static final String uri_token_type_refresh_token = "urn:ietf:params:oauth:token-type:refresh_token";
	public static final String uri_token_type_id_token = "urn:ietf:params:oauth:token-type:id_token";
	public static final String uri_token_type_saml1 = "urn:ietf:params:oauth:token-type:saml1";
	public static final String uri_token_type_saml2 = "urn:ietf:params:oauth:token-type:saml2";
	public static final String uri_token_type_jwt = "urn:ietf:params:oauth:token-type:jwt";

	public static final String reg_redirect_uris = "redirect_uris";
	public static final String reg_token_endpoint_auth_method = "token_endpoint_auth_method";
	public static final String reg_grant_types = "grant_types";
	public static final String reg_response_types = "response_types";
	public static final String reg_client_name = "client_name";
	public static final String reg_client_uri = "client_uri";
	public static final String reg_logo_uri = "logo_uri";
	public static final String reg_scope = "scope";
	public static final String reg_contacts = "contacts";
	public static final String reg_tos_uri = "tos_uri";
	public static final String reg_policy_uri = "policy_uri";
	public static final String reg_jwks_uri = "jwks_uri";
	public static final String reg_jwks = "jwks";
	public static final String reg_software_id = "software_id";
	public static final String reg_software_version = "software_version";
	public static final String reg_client_id = "client_id";
	public static final String reg_client_secret = "client_secret";
	public static final String reg_client_id_issued_at = "client_id_issued_at";
	public static final String reg_client_secret_expires_at = "client_secret_expires_at";
	public static final String reg_registration_access_token = "registration_access_token";
	public static final String reg_registration_client_uri = "registration_client_uri";
	public static final String reg_application_type = "application_type";
	public static final String reg_sector_identifier_uri = "sector_identifier_uri";
	public static final String reg_subject_type = "subject_type";
	public static final String reg_id_token_signed_response_alg = "id_token_signed_response_alg";
	public static final String reg_id_token_encrypted_response_alg = "id_token_encrypted_response_alg";
	public static final String reg_id_token_encrypted_response_enc = "id_token_encrypted_response_enc";
	public static final String reg_userinfo_signed_response_alg = "userinfo_signed_response_alg";
	public static final String reg_userinfo_encrypted_response_alg = "userinfo_encrypted_response_alg";
	public static final String reg_userinfo_encrypted_response_enc = "userinfo_encrypted_response_enc";
	public static final String reg_request_object_signing_alg = "request_object_signing_alg";
	public static final String reg_request_object_encryption_alg = "request_object_encryption_alg";
	public static final String reg_request_object_encryption_enc = "request_object_encryption_enc";
	public static final String reg_token_endpoint_auth_signing_alg = "token_endpoint_auth_signing_alg";
	public static final String reg_default_max_age = "default_max_age";
	public static final String reg_require_auth_time = "require_auth_time";
	public static final String reg_default_acr_values = "default_acr_values";
	public static final String reg_initiate_login_uri = "initiate_login_uri";
	public static final String reg_request_uris = "request_uris";
	public static final String reg_claims_redirect_uris = "claims_redirect_uris";

	public static final String auth_none = "none";
	public static final String auth_client_secret_post = "client_secret_post";
	public static final String auth_client_secret_basic = "client_secret_basic";
	public static final String auth_client_secret_jwt = "client_secret_jwt";
	public static final String auth_private_key_jwt = "private_key_jwt";

	public static final String pkce_plain = "plain";
	public static final String pkce_S256 = "S256";

	public static final String introspect_active = "active";
	public static final String introspect_username = "username";
	public static final String introspect_client_id = "client_id";
	public static final String introspect_scope = "scope";
	public static final String introspect_token_type = "token_type";
	public static final String introspect_exp = "exp";
	public static final String introspect_iat = "iat";
	public static final String introspect_nbf = "nbf";
	public static final String introspect_sub = "sub";
	public static final String introspect_aud = "aud";
	public static final String introspect_iss = "iss";
	public static final String introspect_jti = "jti";
	public static final String introspect_permissions = "permissions";
	public static final String introspect_vot = "vot";
	public static final String introspect_vtm = "vtm";
	public static final String introspect_act = "act";
	public static final String introspect_may_act = "may_act";

	public static final String meta_issuer = "issuer";
	public static final String meta_authorization_endpoint = "authorization_endpoint";
	public static final String meta_token_endpoint = "token_endpoint";
	public static final String meta_jwks_uri = "jwks_uri";
	public static final String meta_registration_endpoint = "registration_endpoint";
	public static final String meta_scopes_supported = "scopes_supported";
	public static final String meta_response_types_supported = "response_types_supported";
	public static final String meta_response_modes_supported = "response_modes_supported";
	public static final String meta_grant_types_supported = "grant_types_supported";
	public static final String meta_token_endpoint_auth_methods_supported = "token_endpoint_auth_methods_supported";
	public static final String meta_token_endpoint_auth_signing_alg_values_supported = "token_endpoint_auth_signing_alg_values_supported";
	public static final String meta_service_documentation = "service_documentation";
	public static final String meta_ui_locales_supported = "ui_locales_supported";
	public static final String meta_op_policy_uri = "op_policy_uri";
	public static final String meta_op_tos_uri = "op_tos_uri";
	public static final String meta_revocation_endpoint = "revocation_endpoint";
	public static final String meta_revocation_endpoint_auth_methods_supported = "revocation_endpoint_auth_methods_supported";
	public static final String meta_revocation_endpoint_auth_signing_alg_values_supported = "revocation_endpoint_auth_signing_alg_values_supported";
	public static final String meta_introspection_endpoint = "introspection_endpoint";
	public static final String meta_introspection_endpoint_auth_methods_supported = "introspection_endpoint_auth_methods_supported";
	public static final String meta_introspection_endpoint_auth_signing_alg_values_supported = "introspection_endpoint_auth_signing_alg_values_supported";
	public static final String meta_code_challenge_methods_supported = "code_challenge_methods_supported";
	public static final String meta_signed_metadata = "signed_metadata";

	public static final String response_mode_query = "query";
	public static final String response_mode_fragment = "fragment";
	public static final String response_mode_form_post = "form_post";
}
