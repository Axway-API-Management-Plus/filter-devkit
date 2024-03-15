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

*Note :* selector expressions in script examples are displayed without the '$' sign (scripts do not work when substitution is applied).

## Attaching resources to the filter

This filter brings the concept of 'Configuration Resource'. Resources are either references to configuration items (Policies, KPS Table, Cache) or simply a selector expression with explicit type coercion. Selector are made available here because script is substituted with an empty message at policy deployment time. To attach resources to the script, an additional tab 'Resources' is added to the script UI. Each resource must be configured with a name which will be used to use it within the script. A set of resources is configured in a [ContextResourceProvider](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/ContextResourceProvider.java). Top level functions are available within the script to interact with the local set of resources.

## Provided runtime functions

Advanced Script Filter provide top level functions to the script to configure behavior and interact with attached resources

 - *getContextResource(string)* : Retrieve an attached resource using its name in the script configuration
 - *getInvocableResource(string)* : Retrieve an attached resource which is invokable (Ex: a policy). If the configured resource does not exists or is not invokable, null is returned.
 - *getFunctionResource(string)* : Retrieve an attached resource which is a function (may come from groovy reflection or script extension). If the configured resource does not exists or is not a function, null is returned.
 - *getKPSResource(string)* : Retrieve an attached resource which is a KPS Table. If the configured resource does not exists or is not a KPS Table, null is returned.
 - *getCacheResource(string)* : Retrieve an attached resource which is a KPS Table. If the configured resource does not exists or is not a KPS Table, null is returned.
 - *invokeResource(message, string)* : Invoke the target resource. It the resource does not exists or is not invokable, null is returned. Otherwise a Boolean is returned reflecting the status of execution. This method may also throw a CircuitAbortException.
 - *substituteResource(dictionary, string)* : Execute selector substitution. It the resource does not exists or is not substitutable null is returned. Even if the substitution triggers an invocation, exception are not thrown (in this case null is returned). The return value is the result of substitution.
 - *getExportedResources()* : Retrieve this script context for exporting in the message (to be used by another script or extension).
 - *getFilterName()* : Retrieve the filter configured name. Purpose of this method is for debugging and diagnostic.

Additional top level function are provided for filter attachment for all script languages

 - *setUnwrapCircuitAbortException(boolean)* : this function can be called in the script attach hook. It allows script to throw CircuitAbortException (which is not available in the base Script Filter). Even if using groovy reflected entry points this option may be set when calling [Script Extensions](Extensions.md)
 - *setExtendedInvoke(boolean)* : this function can be called in the script attach hook. its purpose is to provide the circuit to the invoke function.
 - *reflectExtension(string)* : this function can be called in the script attach hook. its purpose is to bind additional top level functions from script extensions. This function takes a fully qualified interface name as argument.

Groovy scripts define additional runtime functions. Reflection on groovy scripts produce illegal reflection messages in the instance startup log which can be safely ignored.

 - *reflectResources(script)* : reflect the script for methods exported as resources. It must be called with the 'this' keyword as argument. It will search for methods annotated with [InvocableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/InvocableMethod.java), [SubstitutableMethod](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/SubstitutableMethod.java) and [ExtensionFunction](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/context/annotations/ExtensionFunction.java) and will add them to the script's resources. See [Extension Context](Extensions.md) documentation for further information about reflection and injection rules for each annotation.
 - *reflectEntryPoints(script)* : reflect the script for invoke and detach methods. It must be called with the 'this' keyword as argument. It will search for *invoke()* and *detach()* methods and will replace JSR-223 invocation by a direct Java reflection call. Method arguments are injected according to the requested type.

## Runtime usage

Provided runtime functions can be called without any declaration in any language (except for groovies which have specific additional runtime functions). Advanced scripts can be splitted in 3 sections:
 - attach function : called before any message is processed
 - invoke function : called for each handled message
 - detach function : called before undeployment/shutdown (if script needs to release system resources)

Some runtime function are used for configuration, in this first case, those runtime functions are restricted to the attach function. there is no other restrictions. Attach and Detach function are optional and will not generate any error trace if absent.

Example Javascript with the 3 sections :

```javascript
function attach(ctx, entity) {
	// enter configuration directives in this section
	// eventually you can allocate system resources for use in the invoke() function.
}

function invoke(msg) {
	// message handling code (like the regular script filter)
	return true;
}

function detach() {
	// if needed, release allocated system resources
}
```

### Calling policies from script

Calling a policy from a script is a classic use case and is a recurrent problem in customer complex deployments. The GUI of the Advanced Script Filter allows to associate a policy with a name (private to the script's context). A runtime function is dedicated to call this configured policy using the configured context name. Making policies easily available in scripts allows to minimize scripts nodes by orchestrating policy calls from scripts.

```javascript
function invoke(circuit, msg) {
	// call the policy registered under the name 'runtime.sample' in the Advanced Script Filter's Resource GUI
	return invokeResource(msg, "runtime.sample");
}
```

### Resolving selectors from script

Selectors are used everywhere in Filters but are not available in base script syntax. This leads to many errors since selector resolution does not have the same behavior as a message property retrieval. To address this issue, the Filter DevKit allows to declare selector as script resources in the GUI (with a name private to the script as it is done with policies). This way it is possible to use Selector coercion and resolution just like it is done in all filters. Additionally, the Filter DevKit detect and handle incomplete selector resolution (when a trribute does not exists) and will return null instead of '[invalid field]' for Strings.

```javascript
function invoke(circuit, msg) {
	// assume that 'querystring.name' has been bound to expression '{http.querystring.name}' with coercion set to String.
	var name = substituteResource("querystring.name");

	// you code to handle name resolution
	return true;
}
```

### Bind script extension

Script extension is a new mechanism which can add top level functions and resources to the running script. Script Extensions can also interact with current script resources (configured retrieved from an extension or reflected from groovy script). To bind an extension to the current script you call the appropriate runtime function with the script extension interface qualified name as done below :

```javascript
function attach(ctx, entity) {
	reflectExtension("com.vordel.circuit.filter.devkit.script.ext.ScriptExtendedRuntime");
}
```


### Exporting script attached resources

Script attached (and reflected from groovies and extensions) resources can be exported to the message for use either in another script or directly in selectors. The runtime provide a special function to access and export the script's context :

```javascript
function invoke(circuit, msg) {
	msg.put("script.exported", getExportedResources());
	return true;
}
```

Script exported resource include all gathered resource (configuration references and selectors, bound extensions and also reflected groovy methods if any)

### Advanced Script KPS access

This goal of this feature is not to provide a read/write access to KPS (the filter however provide this functionality), but to keep KPS references updated and exported. As a result the Advanced Script Filter will not retrieve the KPS stop using its alias, but its Identity reference. A fragment export of the script with the KPS store will be possible thanks to the KPS reference.

When exporting script context (see above) there is no difference in using regular '${kps[alias]}' and '${script.exported.sample.kps}' (except for the prefix). However the benefit is that the KPS Table is exported and you are sure that the resource provided by the script is valid.

From a script point of view retrieving the resource returns a [KPSResource](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/KPSResource.java) object mirroring the KPS API.

```javascript
function invoke(circuit, msg) {
	// make this script resources available to the message. kps table accessible from expression {script.exported.<resource name>}
	msg.put("script.exported", getExportedResources());

	// assume that 'sample.kps' has been bound to an existing KPS table.
	var kpsTable = getKPSResource("sample.kps");

	// do what you want with the KPS table
	return true;
}
```

### Reflecting resources from script (groovy only)

Code reuse is generally not applicable in the API Gateway context. The Filter DevKit allows policy developer to implements functions as policies and selectors and share them using the above export runtime function. Strong typing must be used on exported functions (explicit types). See See [Extension Context](Extensions.md) documentation for further information about reflection and injection rules. Goal of exporting functions is to reduce script node count and to reuse existing code instead of making cut'n'paste. This runtime function can only be called within the script attachment call.

```groovy
import com.vordel.circuit.Message
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod

def attach(ctx, entity) {
	// reflect script resources
	reflectResources(this)
}

def invoke(msg) {
	// export script context to message
	msg.put("groovy.export", getExportedResources())

	return true
}

@InvocableMethod
public boolean invokeTest(Message msg) {
	// call this method with expression {groovy.export.invokeTest} in an extended eval selector to keep CircuitAbortException relayed
	return true
}

@SubstitutableMethod("substituteTest")
public String helloSubstitution(@SelectorExpression("http.querystring.name") String name) {
	// call this method with expression {groovy.export.substituteTest}
	return String.format("Hello %s !", name)
}

@ExtensionFunction
public String helloFunction(Message msg, String name) {
	// call this method with expression {groovy.export.sayHello(http.querystring.name)}
	return String.format("Hello %s !", name)
}
```

### Unwrapping CircuitAbortException

Unwrapping CircuitAbortException is useful in production when tracking real cause of error. To enable this functionality you can call this function in the script attachment section. It takes either 'true' or 'false' as argument. Setting unwrap to 'true' will be applied to invoke() call and script extensions calls. This runtime function can only be called within the script attachment call.

```javascript
function attach(ctx, entity) {
	setUnwrapCircuitAbortException(true);
}
```

### Being called with the filter Circuit argument

This runtime method is here for compatibility with Script Quick Filters. It allows the Script to access to the Circuit provided to the message call. This runtime function can only be called within the script attachment call.

```javascript
function attach(ctx, entity) {
	setExtendedInvoke(true);
}

function invoke(circuit, msg) {
	// message handling code (like a filter)
	return true;
}
```

### Being called with the filter MessageProcessor (groovy only)

Groovy entry point can be reflected so invoke (and detach) arguments are injected.
 - invoke can accept Circuit, Message and MessageProcessor injection,
 - detach can only accept MessageProcessor injection.

only one invoke function must be defined, and only one detach function must be defined (it remains optional). Strong typing must be used so parameter types are available to the called runtime function. A scriptException is thrown if the runtime is able to detect method but unable to compute injected arguments. When using reflected invoke, CircuitAbortException is implicitely unwrapped, however it remains wrapped when calling extensions unless requested (see above). This runtime function can only be called within the script attachment call.

```groovy
import com.vordel.circuit.CircuitAbortException
import com.vordel.circuit.Message
import com.vordel.circuit.MessageProcessor
import com.vordel.config.Circuit
import com.vordel.config.ConfigContext
import com.vordel.es.Entity

void attach(ConfigContext ctx, Entity entity) {
	// search for invoke() and detach() entry points with injectable parameters
	reflectEntryPoints(this);
}

boolean invoke(Circuit c, Message msg, MessageProcessor p) throws CircuitAbortException {
	// message handling
	return true
}
```



