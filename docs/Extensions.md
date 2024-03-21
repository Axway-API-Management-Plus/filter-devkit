# Extensions

The Filter DevKit comes with rich extensions capabilities. Except for filters, and base runtime, The Filter Devkit extensions does not require to be registered in the Entity Store. They are dynamically discovered on instance startup. There are different kind of extensions:

 - [Extension interface](ExtensionInterface.md) : Exported Java interface callable from custom filters (and and scripts).
 - [Extension context](ExtensionContext.md) : Exported Java methods callable from selectors or scripts (and custom filters).
 - [Script extensions](ScriptExtensions.md) : Additional top level functions and resources for advanced script filter

Each of these extensions are Java class based and may use a [child first ClassLoader](ChildFirstClassLoader.md) so they can be interface with a class path foreign to the API Gateway. This special class loading feture is activated when the extension class is annotated with [ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java).

Extension interfaces and contexts can also be called when the configuration is deployed or undeployed by implementing the [ExtensionModule](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionModule.java).

The annotation processor will take care of referencing annotated classes which need to be registered/instanciated by the Filter Devkit (it creates *META-INF/vordel/extensions* which contains the list of classes to be scanned and a file with the class qualified name in *META-INF/vordel/libraries/* for foreign class path)

Extension loading can be ordered using the standard annotation Priority (see [DynamicCompilerModule](../filter-devkit-dynamic/src/main/java/com/vordel/circuit/filter/devkit/dynamic/DynamicCompilerModule.java) as example for priority declaration.
