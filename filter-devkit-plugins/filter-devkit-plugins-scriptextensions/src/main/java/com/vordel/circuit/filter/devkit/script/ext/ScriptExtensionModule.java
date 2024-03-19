package com.vordel.circuit.filter.devkit.script.ext;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.el.Selector;

@ScriptExtension(ScriptExtendedRuntime.class)
public class ScriptExtensionModule implements ScriptExtendedRuntime {
	private static final Selector<String> HTTP_VERB = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_VERB, String.class);
	private static final Selector<Integer> HTTP_STATUS = SelectorResource.fromExpression(MessageProperties.HTTP_RSP_STATUS, Integer.class);

	@Override
	public String getHTTPVerb(Message msg) {
		return HTTP_VERB.substitute(msg);
	}

	@Override
	public void setHTTPVerb(Message msg, String verb) {
		if (verb == null) {
			msg.remove(verb);
		} else {
			msg.put(MessageProperties.HTTP_REQ_VERB, verb);
		}
	}

	@Override
	public int getHTTPStatus(Message msg) {
		Integer status = HTTP_STATUS.substitute(msg);

		return status == null ? 500 : status;
	}

	@Override
	public void setHTTPStatus(Message msg, int status) {
		msg.put(MessageProperties.HTTP_RSP_STATUS, status);
		msg.put(MessageProperties.HTTP_RSP_INFO, HTTPPlugin.getResponseText(status));
	}
}
