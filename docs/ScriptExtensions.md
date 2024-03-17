# Script Extensions

Script extensions is a mechanism which add top level functions to scripts. It applies to all supported languages. Main goal of this feature is to provide the ability for Java extensions to access Script resources without playing with context exports. It allows Java Code to call policies, use KPS and Caches with Policy Studio exported references.

Implementing this kind of extension is quite simple :
 - Define an interface which contains methods to be exported to script (be aware that methods using variable list of arguments can't be exported to Javascript),
 - Implement this interface in a concrete class (If you need you extension to access script resources, extend the abstract class [AbstractScriptExtension](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/extension/AbstractScriptExtension.java) (keep and relay the single arg default constructor to the original abstract class),
 - Annotate you class with [ScriptExtension](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/script/extension/ScriptExtension.java). Do no forget to specify implemented interfaces to the annotation.

For each encountered [ScriptExtension](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/script/extension/ScriptExtension.java), interfaces will get registered by the extension loader. To bind a script extension to a specific script, you must use the *reflectExtension(name)* runtime function with the interface qualified name as argument. 

Additionally if the implementation contains methods annotated with [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) or [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) will be added to the current script resources set (existing resource are replaced if set).

Avoid mixing methods exported for both resources and script top level function.
