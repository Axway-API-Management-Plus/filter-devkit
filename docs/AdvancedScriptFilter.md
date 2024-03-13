# Advanced Script Filter

Goal of the Advanced Script Filter is to avoid use of Custom Filters whenever possible, because most problems of Custom Filters comes from GUI maintenance and deployment in the policy studio.

The Advanced script filter is a feature which extends the classing scripting filter in order to provide the following functionalities:
 - Call Policies,
 - Resolve Selectors,
 - Retrieve a reference to a KPS Table with read/write API,
 - Retrieve a reference to a Cache with read/write API,
 - Attachment hook (before handling messages)
 - Detachment hook (before shutdown or re-deployment)
 - Throw real CircuitAbortException.
 - Export resources for use in another script (of filter),

Additionally, Groovy script can be reflected to export methods as Selectors (like extension plugins).

To implement this, functions are added to the script at top level. Technically, those functions are closures of the [AdvancedScriptRuntime](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/advanced/AdvancedScriptRuntime.java) class. All functions except reflective ones are available to all supported scripting languages. Since groovy is close to Java, reflection is available and useable as long as strong typing is used.

## Attaching resources to the filter

This filter brings the concept of 'Configuration Resource'. Resources are either references to configuration items (Policies, KPS Table, Cache) or simply a selector expression with explicit type coercion. Selector are made available here because script is substituted with an empty message at policy deployment time. To attach resources to the script, an additional tab 'Resources' is added to the script UI. Each resource must be configured with a name which will be used to use it within the script. A set of resources is configured in a [ContextResourceProvider](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/ContextResourceProvider.java). Top level functions are available within the script to interact with the local set of resources.

## Provided runtime functions

Advanced Script Filter provide top level functions to the script to configure behavior and interact with attached resources

 - *getContextResource(string)* : Retrieve an attached resource using its name in the script configuration
 - *getInvocableResource(string)* : Retrieve an attached resource which is invokable (Ex: a policy). If the configured resource does not exists or is not invokable, null is returned.
 - *getKPSResource(string)* : Retrieve an attached resource which is a KPS Table. If the configured resource does not exists or is not a KPS Table, null is returned.
 - *getCacheResource(string)* : Retrieve an attached resource which is a KPS Table. If the configured resource does not exists or is not a KPS Table, null is returned.
 - *invokeResource(string)* : Invoke the target resource. It the resource does not exists or is not invokable, null is returned. Otherwise a Boolean is returned reflecting the status of execution. This method may also throw a CircuitAbortException.
 - *substituteResource(string)* : Execute selector substitution. It the resource does not exists or is not substitutable null is returned. Even if the substitution triggers an invocation, exception are not thrown (in this case null is returned). The return value is the result of substitution.
 - *getExportedResources()* : Retrieve this script context for exporting in the message (to be used by another script or extension).
 - *getFilterName()* : Retrieve the filter configured name. Purpose of this method is for debugging and diagnostic.

Additional top level function are provided for filter attachment for all script languages

 - *setUnwrapCircuitAbortException((boolean)* : this function can be called in the script attach hook. It allows script to throw CircuitAbortException (which is not available in the base Script Filter). Even if using groovy reflected entry points this option may be set when calling [Script Extensions](Extensions.md)
 - *setExtendedInvoke(boolean)* : this function can be called in the script attach hook. its purpose is to provide the circuit to the invoke function.
 - *reflectExtension(string)* : this function can be called in the script attach hook. its purpose is to bind additional top level functions from script extensions. This function takes a fully qualified interface name as argument.

Groovy scripts define additional runtime functions. Reflection on groovy scripts produce illegal reflection messages in the instance startup log which can be safely ignored.

 - *reflectResources(script)* : reflect the script for methods exported as resources. It must be called with the 'this' keyword as argument. It will search for methods annotated with [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) and [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) and will add them to the script's resources. See [Extension Context](Extensions.md) documentation for further information about reflection and injection rules for each annotation.
 - *reflectEntryPoints(script)* : reflect the script for invoke and detach methods. It must be called with the 'this' keyword as argument. It will search for *invoke()* and *detach()* methods and will replace JSR-223 invocation by a direct Java reflection call. Method arguments are injected according to the requested type.

