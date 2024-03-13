# Extensions

The Filter DevKit comes with rich extensions capabilities. Except for filters, and base runtime, The Filter Devkit extensions does not require to be registered in the Entity Store. They are dynamically discovered on instance startup. There are different kind of extensions:

 - Extension interface : Exported Java interface callable from custom filters (and and scripts).
 - Extension context : Exported Java methods callable from selectors or scripts (and custom filters).
 - Script extensions : Additional top level functions and resources for advanced script filter

Each of these extensions are Java class based and may use a child first ClassLoader so they can be interface with a class path foreign to the API Gateway. This special class loading feture is activated when the extension class is annotated with [@ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java).

Extension interfaces and contexts can also be called when the configuration is deployed or undeployed by implementing the [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java).

The annotation processor will take care of referencing annotated classes which need to be registered/instanciated by the Filter Devkit (it creates *META-INF/vordel/extensions* which contains the list of classes to be scanned and a file with the class qualified name in *META-INF/vordel/libraries/* for foreign class path)

## Extension interface

Extension interfaces is a mechanism dedicated for custom filters and regular script filter. Main goal if this feature is to instanciate a module from a foreign class loader with a particular interface shared with API Gateway ClassPath and Module ClassPath. Using this feature is quite simple and can be used in other situations since the child first class loader remains optional.

 - The programmer start by creating an interface which will be exported. This interface must not inherit [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java)
 - He also create an implementation for this interface with a no-arg constructor and the [@ExtensionModulePlugin](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionModulePlugin.java) annotation with the exported interface as value.
 - The implementation may also implement the [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java) interface
 - The implementation may also be annotated by [@ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java) if a child first class loader is needed.

Compilation must be done with the [ExtensionProviderGenerator](../filter-devkit-tools/src/main/java/com/vordel/circuit/filter/devkit/context/tools/ExtensionProviderGenerator.java) annotation processor activated (generates Filter DevKit registration files)

If no interface is provided in [@ExtensionModulePlugin](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionModulePlugin.java), the class will be instanciated but not registered.

In custom filter code, the interface instance is retrieved using the *ExtensionLoader.getExtensionInstance()* static call.

Here is an example implementation :

```java
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.context.ExtensionModule;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionModulePlugin;
import com.vordel.config.ConfigContext;
import com.vordel.es.EntityStoreException;

/**
 * sample interface which will be shared by the API Gateway and the module
 */
public interface ExtensionInterfaceSample {
	public String sayHello();
}

/**
 * interface implementation with annotation and no arg constructor. Each interface specified in the
 * annotation value will get registered
 */
@ExtensionModulePlugin(ExtensionInterfaceSample.class)
class ExtensionInterfaceSampleImpl implements ExtensionInterfaceSample, ExtensionModule {
	@Override
	public String sayHello() {
		return "Hello";
	}

	@Override
	public void attachModule(ConfigContext ctx) throws EntityStoreException {
		/*
		 * since this sample implements the ExtensionModule interface, this method is
		 * called when the configuration is loaded
		 */
	}

	@Override
	public void detachModule() {
		/*
		 * since this sample implements the ExtensionModule interface, this method is
		 * called when the configuration is unloaded (API Gateway shutdown, restart or
		 * before new deployment)
		 */
	}
}

class ExtentionInterfaceSampleCall {
	public static String callSayHello() {
		/* retrieve the registered implementation */
		ExtensionInterfaceSample impl = ExtensionLoader.getExtensionInstance(ExtensionInterfaceSample.class);
		
		/* and call the interface */
		return impl.sayHello();
	}
}
```

## Extension Context

Extension Context is a mechanism dedicated to call Java code from selectors. In order to fully use this mechanism, the [Extended Evaluate Selector](../filter-devkit-plugins/filter-devkit-plugins-eval/README.md) is recommended (as this plugin is able to relay CircuitAbortException).

There is 3 kinds of exported selectors
 - [Invocable Selector](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) : invoke a Java method like a policy shortcut using an (Extented) Eval Selector Filter. Parameters are resolved from annotations and injected.
 - [Substitutable Selector](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) : invoke a Java method to return any value. This kind of export can't throw exceptions but can be used anywhere in the API Gateway where selectors are accepted (Set Message, Copy/Modify, Set Attribute, etc...). Parameters are resolved from annotations and injected.
 - [Extension Function](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) : Invoke a Java method using JUEL invoke syntax. Parameters must be provided in the JUEL expression. In this last case parameters coercion may not be accurate. Use with caution (especially with variable arguments or long and double numbers).

Selector syntax use an API Gateway global namespace.

```
// syntax for invocable where name is the extension name and exportedInvocable the exported method
${extensions['name'].exportedInvocable}
// syntax for substitutable where name is the extension name and exportedSubstitutable the exported method
${extensions['name'].exportedSubstitutable}
// syntax for function where name is the extension name and exportedFunction the exported method
// arguments are also JUEL expressions
${extensions['name'].exportedFunction(arg1, arg2, arg3}
```

Reflection rules :

 - A method is marked as exported to the extension context if any of [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) annotations are seen on a given method,
 - All exported methods must be public,
 - If the [ExtensionModulePlugin](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionModulePlugin.java) annotation is not used, only static method export is allowed. Otherwise, the class is instanciated with the no-arg constructor,
 - Annotations must be set on concrete or abstract classes not on interfaces,
 - If [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) is set on a method, [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) and [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) cannot be set,
 - If an annotated method is overridden, it will inherit annotations from original method if no new annotations ([InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java)) are defined on it,
 - for [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) and [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java), Message (or Dictionary) is the only parameter injectable without annotation. Other parameters must be annotated either with [DictionaryAttribute](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/DictionaryAttribute.java) or [SelectorExpression](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SelectorExpression.java)
 - for [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java), the first parameter must be the Message. when calling from a selector, the message will be prepended to the argument list prior to function invocation.
 - [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) can only return booleans,
 - [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) and [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) can return any object.

Here is an example implementation :

```java
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContextPlugin;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionModulePlugin;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.common.Dictionary;

/**
 * sample extension context for API Gateway. This extension will get registered
 * under the 'sample' name. The @ExtensionModulePlugin annotation allows this
 * class to be instanciated with the no-arg constructor (but it will not
 * register any interface). Since it does not implements ExtensionModule, there
 * will be no attachModule() or detachModule() initialization calls.
 * 
 * Without @ExtensionModulePlugin all exported methods must be set as static
 */
@ExtensionModulePlugin
@ExtensionContextPlugin("sample")
public class ExtensionContextSample {
	/**
	 * example export for a invocable method. all parameters are injected
	 * 
	 * @param msg  current message
	 * @param arg1 the message attribute 'attr1' will be retrieved from the message
	 *             and coerced to requested type (String)
	 * @param arg2 the selector expression ${attr2} will be applied on the message
	 *             and coerced to requested type (String)
	 * @return always true
	 * @throws CircuitAbortException if any error occurs
	 */
	@InvocableMethod
	public boolean invokeTest(Message msg, @DictionaryAttribute("attr1") String arg1, @SelectorExpression("attr2") String arg2) throws CircuitAbortException {
		// call this method with ${extensions['sample'].invokeTest}
		return true;
	}

	/**
	 * example export for a substitutable method. all parameters are injected. The
	 * annotation have defined export name which will be used instead of the method
	 * name.
	 * 
	 * @param msg  should be current message (when called outside script), but can
	 *             be null since script invocation allows to use Dictionary instead
	 * @param dict selector dictionary (generally this is the current message)
	 * @param arg1 the message attribute 'attr1' will be retrieved from the
	 *             dictionary and coerced to requested type (String)
	 * @param arg2 the selector expression ${attr2} will be applied on the
	 *             dictionary and coerced to requested type (String)
	 * @return "hello" constant
	 */
	@SubstitutableMethod("substituteTest")
	public String sayHello(Message msg, Dictionary dict, @DictionaryAttribute("attr1") String arg1, @SelectorExpression("attr2") String arg2) {
		// call this method with ${extensions['sample'].substituteTest}
		return "hello";
	}

	/**
	 * example export for a function call. Only the first parameter is injected (can
	 * be Message or Dictionary). If Dictionary if provided in a script and Message
	 * is requested in the call, null will be provided as first argument. This
	 * example is typically used in an (Extended) Eval Selector.
	 * 
	 * @param msg  current message (when called outside script)
	 * @param arg1 provided and coerced to requested value using JUEL expression
	 * @param arg2 provided and coerced to requested value using JUEL expression
	 * @param arg3 provided and coerced to requested value using JUEL expression
	 * @return always true
	 * @throws CircuitAbortException if any error occurs
	 */
	@ExtensionFunction
	public boolean functionTest(Message msg, String arg1, String arg2, String arg3) throws CircuitAbortException {
		// call this method with ${extensions['sample'].functionTest(arg1, arg2, arg3)}
		return true;
	}

	/**
	 * example export for a function call. Only the first parameter is injected (can
	 * be Message or Dictionary). If Dictionary if provided in a script and Message
	 * is requested in the call, null will be provided as first argument. This
	 * example is typically used in an Set Message Filter.
	 * 
	 * @param msg  current message (when called outside script)
	 * @param name use name to saye hello
	 * @return say hello to given name
	 * @throws CircuitAbortException
	 */
	@ExtensionFunction
	public String sayHello(Message msg, String name) throws CircuitAbortException {
		// call this method with ${extensions['sample'].sayHello(http.querystring.name)}
		return String.format("Hello %s !", name);
	}
}
```

