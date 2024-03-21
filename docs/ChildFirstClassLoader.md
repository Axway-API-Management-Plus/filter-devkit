# Child First Class Loader

This feature is the most complex to use. Its purpose is to protect the API Gateway from conflicting dependencies brought by large libraries.

It can be used only on [Java Extensions](Extensions.md) packaged as jars ([Dynamic Compiler](../filter-devkit-dynamic/README.md) cannot compile such extension). It works by adding an [ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java) annotation to an extension registered type ([ExtensionInstance](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionInstance.java), [ExtensionContext](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionContext.java) or [ScriptExtension](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/script/extension/annotations/ScriptExtension.java)).

Presence of this annotation will trigger the annotated class to be forwarded to a private class loader inheriting the API Gateway class path with additional jars or class directories.

The complexity of this feature relies on the fact that classes present in the jar seen by the API Gateway will be loaded by the regular class loader, only the module class is loaded in the private class loader.

To address this issue, there is the following alternatives (pick one) :
 - Implement your module within a single class (with no inner or anonymous class), This is the simplest solution.
 - add binary name of needed classes in the 'classes' value of the [ExtensionLibraries](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLibraries.java) (values are for selector directories expression, classes are for binary names)
 - annotate needed classes using [ExtensionLink](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionLink.java)

Anyway, the annotation processor will take care of building list of alternate needed jar and classes for the module, and the extension scanner will take care of creating needed class loaders with appropriate classes.

Keep also in mind that for script extensions and registered instances, the registered interface must not have any reference to the foreign classes (however, implementation can use and abuse of it).
