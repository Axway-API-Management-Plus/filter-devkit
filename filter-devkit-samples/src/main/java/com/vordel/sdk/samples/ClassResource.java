package com.vordel.sdk.samples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.bind.ExtensionPlugin;
import com.vordel.circuit.script.bind.InvocableMethod;
import com.vordel.circuit.script.bind.SelectorExpression;
import com.vordel.circuit.script.bind.SubstitutableMethod;

@ExtensionPlugin("hello.module")
public class ClassResource {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@InvocableMethod("Hello")
	public static boolean service(Message msg, @SelectorExpression("http.querystring.name") String name) throws CircuitAbortException {
		boolean result = false;

		if (name != null) {
			ObjectNode node = MAPPER.createObjectNode();

			node.put("hello", name);
			msg.put(MessageProperties.CONTENT_BODY, ScriptHelper.toJSONBody(node));

			result = true;
		}

		return result;
	}

	@SubstitutableMethod("HelloMessage")
	public static String getHelloMessage(@SelectorExpression("http.querystring.name") String name) {
		return name == null ? null : String.format("Hello %s !", name);
	}
}
