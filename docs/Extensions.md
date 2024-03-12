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

Extension Context is a mechanism dedicated to call Java code from selectors. In order to fully use this mechanism, the [Extended Evaluate Selector](../filter-devkit/filter-devkit-plugins/filter-devkit-plugins-eval/README.md) is recommended (as this plugin is able to relay CircuitAbortException).

There is 3 kinds of exported selectors
 - [Invocable Selector](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java) : invoke a Java method like a policy shortcut using an (Extented) Eval Selector Filter. Parameters a resolved from annotations and injected.
 - [Substitutable Selector](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) : invoke a Java method to return any value. This kind of export can't throw exceptions but can be used anywhere in the API Gateway where selectors are accepted (Set Message, Copy/Modify, Set Attribute, etc...). Parameters a resolved from annotations and injected.
 - [Extension Function](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) : Invoke a Java method using JUEL invoke syntax. Parameters must be provided in the JUEL expression. In this last case parameters are coerced and may not be accurate. Use with caution (especially with variable arguments).

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

