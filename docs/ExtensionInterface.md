# Extension interface

Extension interfaces is a mechanism dedicated for custom filters and regular script filter. Main goal if this feature is to instanciate a module from a foreign class loader with a particular interface shared with API Gateway ClassPath and Module ClassPath. Using this feature is quite simple and can be used in other situations since the child first class loader remains optional.

 - The programmer start by creating an interface which will be exported. This interface must not inherit [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java)
 - He also create an implementation for this interface with a no-arg constructor and the [ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java) annotation with the exported interface as value.
 - The implementation may also implement the [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java) interface
 - The implementation may also be annotated by [ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java) if a child first class loader is needed.

Compilation must be done with the [ExtensionProviderGenerator](../filter-devkit-tools/src/main/java/com/vordel/circuit/filter/devkit/context/tools/ExtensionProviderGenerator.java) annotation processor activated (generates Filter DevKit registration files)

If no interface is provided in [ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java), the class will be instanciated but not registered.

In custom filter code, the interface instance is retrieved using the *ExtensionLoader.getExtensionInstance()* static call.

Here is an example implementation :

```java
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.context.ExtensionModule;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
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
@ExtensionInstance(ExtensionInterfaceSample.class)
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
