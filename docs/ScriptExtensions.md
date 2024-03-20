# Script Extensions

Script extensions is a mechanism which add top level functions to scripts. It applies to all supported languages. Main goal of this feature is to provide the ability for Java extensions to access Script resources without playing with context exports. It allows Java Code to call policies, use KPS and Caches with Policy Studio exported references.

Implementing this kind of extension is quite simple :
 - Define an interface which contains methods to be exported to script (be aware that methods using variable list of arguments can't be exported to Javascript),
 - Implement this interface in a concrete class,
 - If you need your extension to add resources to the calling script, your concrete class must extend [ScriptExtensionConfigurator](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/extension/ScriptExtensionConfigurator.java),
 - If you need your extension to access script resources, extend the abstract class [AbstractScriptExtension](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/extension/AbstractScriptExtension.java) instead of [ScriptExtensionConfigurator](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/extension/ScriptExtensionConfigurator.java) (keep and relay the single arg default constructor to the original abstract class),
 - Annotate you class with [ScriptExtension](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/script/extension/annotations/ScriptExtension.java). Do no forget to specify implemented interfaces to the annotation.
 - Override the attachResources() method to add resources to the calling script (if applicable).

For each encountered [ScriptExtension](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/script/extension/annotations/ScriptExtension.java), interfaces will get registered by the extension loader. To bind a script extension to a specific script, you must use the *attachExtension(name)* runtime function of the [advanced script filter](AdvancedScriptFilter.md) with the interface qualified name as argument or (if using groovy script in default filter) use the the *attachExtension(name)* method of the builder callback with interface qualified name as argument within [script context](ScriptContext.md) creation callback.

Additionally if the implementation contains methods annotated with [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) will be added to the current script resources set (existing resource are replaced).

Avoid mixing methods exported for both resources and script top level function.

## Simple Example

```java
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;

public interface ScriptExtensionSampleInterface {
	String sayHello();
}

@ScriptExtension(ScriptExtensionSampleInterface.class)
class ScriptExtensionSampleModule implements ScriptExtensionSampleInterface {
	@Override
	public String sayHello() {
		return "hello";
	}
}
```

which can be called in JS (using [Advanced script filter](AdvancedScriptFilter.md)) :

```javascript
function attach(ctx, entity) {
	attachExtension("ScriptExtensionSampleInterface");
}

function invoke(msg) {
	com.vordel.trace.Trace.info(sayHello());

	return true;
}
```

and also Groovy (using default script filter) :

```groovy
import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder
import com.vordel.trace.Trace

ScriptContextBuilder.bindGroovyScriptContext(this, { builder ->
	builder.attachExtension("ScriptExtensionSampleInterface")
})

def invoke(msg) {
	Trace.info(sayHello())

	return true
}
```

## Interacting example

In this example, the groovy script export a resource usable by the extension.

Class implementation :

```java
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;

public interface InteractingScriptExtensionSampleInterface {
	String sayHello(Message msg);
}

@ScriptExtension(InteractingScriptExtensionSampleInterface.class)
class InteractingScriptExtensionSampleModule extends AbstractScriptExtension implements InteractingScriptExtensionSampleInterface {
	protected InteractingScriptExtensionSampleModule(ScriptExtensionBuilder builder) {
		super(builder);
	}

	@Override
	public String sayHello(Message msg) {
		// substitute 'who' resource from the script
		Object who = substituteResource(msg, "who");
		
		return String.format("hello %s !", who);
	}
}
```

Groovy script (using [Advanced script filter](AdvancedScriptFilter.md)) :

```groovy
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod
import com.vordel.trace.Trace

def attach(ctx, entity) {
	reflectResources(this)
	attachExtension("InteractingScriptExtensionSampleInterface")
}

def invoke(msg) {
	Trace.info(sayHello(msg))

	return true
}

@SubstitutableMethod("who")
String getWorld() {
	return "world"
}
```

Groovy (using default script filter) :

```groovy
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod
import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder
import com.vordel.trace.Trace

ScriptContextBuilder.bindGroovyScriptContext(this, { builder ->
	builder.attachExtension("InteractingScriptExtensionSampleInterface")
})

def invoke(msg) {
	Trace.info(sayHello(msg))

	return true
}

@SubstitutableMethod("who")
String getWorld() {
	return "world"
}
```

