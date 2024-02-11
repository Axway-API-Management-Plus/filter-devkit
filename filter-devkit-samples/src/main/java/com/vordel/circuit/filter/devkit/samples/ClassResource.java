package com.vordel.circuit.filter.devkit.samples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContextPlugin;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.script.ScriptHelper;

@ExtensionContextPlugin("hello.module")
public class ClassResource {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * This method will be callable within script or using an eval selector using
	 * ${extensions['hello.module'].Hello}
	 * 
	 * @param msg  injected message
	 * @param name injected result of selector <code>${http.querystring.name}</code>
	 * @return a json object containing a "hello" property with provided name as
	 *         value
	 * @throws CircuitAbortException if any error occurs
	 */
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

	/**
	 * This method can be called anywhere you can use a selector (Set Attribute, Set
	 * Message, etc...). It will return a string value. It is useable with the
	 * expression <code>${extensions['hello.module'].HelloMessage}</code>.
	 * 
	 * @param name injected result of selector <code>${http.querystring.name}</code>
	 * @return an Hello with name message
	 */
	@SubstitutableMethod("HelloMessage")
	public static String getHelloMessage(@SelectorExpression("http.querystring.name") String name) {
		return name == null ? null : String.format("Hello %s !", name);
	}
}
