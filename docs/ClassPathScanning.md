# Pluggable module support 

**Please note that this feature may change from ClassPath scanning to ServiceLoader implementation. Except for a new annotation to be present on the classes implementing ExtensionModule there should be no change on implementation.**

The Pluggable module support feature allows to execute code on deployment events (attach/detach) and to export Methods to be executed from scripts or selector substitution.

Pulggable modules are discovered using classpath scanning. There is actually 2 kinds of Pluggable modules:
 - Extension Modules : Execute code on deployment events
 - Extension Plugins : Register methods to be invoked from scripts or selectors

Once classpath scanning has been done, discovered classes are sorted using the optional annotation 'javax.annotation.Priority'. If absent a default priority of zero is assumed. The lowest priority is registered first.

## Extension Modules

Extensions modules must implements the interface ExtensionModule. Only concrete class which inherits this interface will be processed.

```java
package com.vordel.circuit.script.bind;

import com.vordel.config.ConfigContext;
import com.vordel.es.EntityStoreException;

public interface ExtensionModule {
	public void attachModule(ConfigContext ctx) throws EntityStoreException;

	public void detachModule();
}
```

When a extension module is registered, the class is first instanciated using the no-arg constructor. Once the class has been created, the attachModule() is called with the current config context as argument. No Entity is provided since there is no registration in the entity store.

When a new configuration is deployed all modules which where initialized using the attachModule() call are detached in the reverse order of the initialization using the detachModule() call.

It is not possible to access to an ExtensionModule from policies or scripts unless the class is annotated with @ExtensionPlugin.

## Extension Plugins

Extension plugins allows to export Java Methods to be used by API Gateway scripts and selectors. Method arguments are dynamically injected from the running context.

When a class has the @ExtensionPlugin extension, all methods which are 'public' and 'static' are exportable. If a class inherits ExtensionModule and has the annotation @ExtensionPlugin, all 'public' methods becomes exportable.

There is two kind of exportable methods:
 - @InvocableMethod : This kind of method can throw a CircuitAbortException and must return a boolean
 - @SubstitutableMethod : This kind of method can return any object, but is not able to throw an exception (null is assumed as return value)

All extensions are available in messages under the global dictionary 'extensions'

### Extension Plugin example

```java
package com.vordel.circuit.filter.devkit.samples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionPlugin;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.script.ScriptHelper;

@ExtensionPlugin("hello.module")
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

```

The above class expose two methods under the extension 'hello.module'.

The method 'HelloMessage' will be called by the selector substitution ${extensions["hello.module"].HelloMessage}. The 'name' parameter of the method will be injected using string coercion of the selector expression ${http.querystring.name}. You should use and abuse of substituable methods whenever possible.

The method 'Hello' is available to scripts (advanced or not) and eval selector (since it is patched by the FDK). The following regular script shows how to call an exported invocable method.

```groovy
import com.vordel.circuit.Message
import com.vordel.circuit.filter.devkit.context.ExtensionContext
import com.vordel.circuit.filter.devkit.context.ExtensionLoader

import groovy.transform.Field

/**
 * retrieve registered extension
 */
@Field final ExtensionContext EXTENSION = ExtensionLoader.getGlobalResources("hello.module");

/**
 * script entry point.
 *
 * @param msg message being handled
 * @return result of the Hello method
 */
boolean invoke(Message msg) {
	/* invoke the exported 'Hello' method */
	return EXTENSION.invoke(msg, "Hello");
}
```

Usage of invocable method is encouraged when combined with Advanced Scripting Context Call filter. Purpose of this feature is to build sharable components. Usage is discouraged in regular scripting filter because of Exception handling.
