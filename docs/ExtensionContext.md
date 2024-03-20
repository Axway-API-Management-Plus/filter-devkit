# Extension context

Extension Context is a mechanism dedicated to call Java code from selectors. In order to fully use this mechanism, the [Extended Evaluate Selector](../filter-devkit-plugins/filter-devkit-plugins-eval/README.md) is recommended (as this plugin is able to relay CircuitAbortException).

If not using the Filter Devkit loadable module the following limitations apply :
 - Only static method export allowed,
 - No global namespace registration (explicit reflection of Java classes within groovies),
 - No 'child first' class loader available (depends on [Extension interface](ExtensionInterface.md)).

There is 3 kinds of exported methods
 - [Invocable method](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) : invoke a Java method like a policy shortcut using an (Extended) Eval Selector Filter. Parameters are resolved from annotations and injected.
 - [Substitutable method](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) : invoke a Java method to return any value. This kind of export can't throw exceptions but can be used anywhere in the API Gateway where selectors are accepted (Set Message, Copy/Modify, Set Attribute, etc...). Parameters are resolved from annotations and injected.
 - [Extension Function](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) : Invoke a Java method using JUEL invoke syntax. Parameters must be provided in the JUEL expression. Use with caution since parameter coercion may be tricky and variable arguments can cause strange behavior when used outside of Java Strong typing system (like Javascript or JUEL).

Extension context global registration use an API Gateway global namespace. This global registration is only available when using the Filter Devkit module has been imported in the typeset. Script exported contexts do not use the global namespace they use a message attribute instead. See examples below for global registration.

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
 - If the [ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java) annotation is not used, only static method export is allowed. Otherwise, the class is instanciated with the no-arg constructor. [ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java),
 - Annotations must be set on concrete or abstract classes not on interfaces,
 - If [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) is set on a method, [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) and [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) cannot be set,
 - If an annotated method is overridden, it will inherit annotations from original method if no new annotations ([InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java)) are defined on it,
 - for [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) and [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java), Message (or Dictionary) is the only parameter injectable without annotation. Other parameters must be annotated either with [DictionaryAttribute](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/DictionaryAttribute.java) or [SelectorExpression](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SelectorExpression.java)
 - for [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java), the first parameter must be the Message. when calling from a selector, the message will be prepended to the argument list prior to function invocation.
 - [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) can only return booleans,
 - [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) and [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) can return any object.
 - It is not possible to export 2 different methods with the same resource name.

For implementing :
 - The programmer start by creating an concrete class annotated with [ExtensionContext](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionContext.java),
 - according the methods implementation he selects export types for each method using [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java).
 - The class may also be annotated by [ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java) if non static methods need to be exported,
 - The class may also be annotated by [ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java) if a child first class loader is needed.

For groovy scripts contexts ExtensionContext and ExtensionInstance are not needed. The script is always an instance and a runtime function is provided to reflect exported methods. Also ExtensionLibraries is not available for Groovy.

Here is an example implementation (instanciated) :

```java
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.common.Dictionary;

/**
 * sample extension context for API Gateway. This extension will get registered
 * under the 'sample' name. The @ExtensionInstance annotation allows this class
 * to be instanciated with the no-arg constructor (but it will not register any
 * interface). Since it does not implements ExtensionModule, there will be no
 * attachModule() or detachModule() initialization calls.
 * 
 * Without @ExtensionInstance all exported methods must be set as static
 */
@ExtensionInstance
@ExtensionContext("sample")
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
