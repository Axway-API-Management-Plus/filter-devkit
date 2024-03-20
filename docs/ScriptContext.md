# Script context

[ScriptContext](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/context/ScriptContext.java) is a feature dedicated to be used in default API Gateway script filter. It's aimed for configurations which can't use the base typeset of the Filter Devkit. The underlying context object is not available in the script. Instead additional top level functions are added to the script like in the [Advanced script filter](AdvancedScriptFilter.md).

The following additional methods are added to the script when using this feature :
 - *getContextResource(string)* : Retrieve an attached resource using its name in the script configuration
 - *getInvocableResource(string)* : Retrieve an attached resource which is [invocable](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/InvocableResource.java) (Ex: a [policy](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/PolicyResource.java)). If the configured resource does not exists or is not invokable, null is returned.
 - *getFunctionResource(string)* : Retrieve an attached resource which is a [function](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/FunctionResource.java) (may come from groovy reflection or script extension). If the configured resource does not exists or is not a function, null is returned.
 - *getSubstitutableResource(string)* : Retrieve an attached resource which is a [substitutable](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/SubstitutableResource.java) (Ex: a [selector](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/SelectorResource.java) expression). If the configured resource does not exists or is not substitutable, null is returned.
 - *getKPSResource(string)* : Retrieve an attached resource which is a [KPS Table](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/KPSResource.java). If the configured resource does not exists or is not a KPS Table, null is returned.
 - *getCacheResource(string)* : Retrieve an attached resource which is a [Cache](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/CacheResource.java). If the configured resource does not exists or is not a Cache, null is returned.
 - *invokeResource(message, string)* : Invoke the target resource. It the resource does not exists, null is returned. If the method is not [invocable](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/InvocableResource.java) an exception is thrown. Otherwise a Boolean is returned reflecting the status of execution. The target resource may also throw a CircuitAbortException.
 - *invokeFunction(dictionary, string, variable arguments)* : Invoke the target [function](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/FunctionResource.java). It the resource does not exists, null is returned. If the resource is not a function, an exception is thrown. Otherwise the function return value is returned. The target function may also throw a CircuitAbortException.
 - *substituteResource(dictionary, string)* : Execute selector substitution. It the resource does not exists or is not substitutable null is returned. Even if the substitution triggers an invocation, exception are not thrown (in this case null is returned). The return value is the result of substitution.
 - *getExportedResources()* : Retrieve this script context for exporting in the message (to be used by another script or extension). Even is selector substitution of exported resources includes [caches](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/CacheResource.java) and [kps](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/resources/KPSResource.java) the exported object do not have shorthand methods *getKPSResource(string)* and *getCacheResource(string)* which are only valid as script top level functions.

The [ScriptContext](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/context/ScriptContext.java) brings to existing groovies the reflection capabilities of [extension context](ExtensionContext.md). It works by adding a top level declaration to the script with a callback groovy closure. The callback closure receive a [ScriptContextBuilder](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/context/ScriptContextBuilder.java) object which have methods to attach resources to the script context.

As Policy Studio references are not available, resource binding is done with text constant references. When using this feature, annotations and runtime libraries must be added to the Policy Studio runtime dependencies.

```groovy
import com.vordel.circuit.Message
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod
import com.vordel.circuit.filter.devkit.script.PolicyHelper
import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder
import com.vordel.common.Dictionary;
import com.vordel.trace.Trace

// binds base script runtime methods into groovy script
// also reflect groovy script exportable methods
ScriptContextBuilder.bindGroovyScriptContext(this, { builder ->
	// binds 3 resources to this script
	builder.attachSelectorResourceByExpression("selector.name", "http.querystring.name", String.class)
	builder.attachPolicyByPortableESPK("policy.healthcheck1", "<key type='CircuitContainer'><id field='name' value='Policy Library'/><key type='FilterCircuit'><id field='name' value='Health Check'/></key></key>")
	builder.attachPolicyByShorthandKey("policy.healthcheck2", "/[CircuitContainer]name=Policy Library/[FilterCircuit]name=Health Check")

	// additional methods for registering resources
	//builder.attachKPSResourceByAlias("resource.kps", "kpsAlias") // register KPS Table using its alias
	//builder.attachCacheResourceByName("resource.cache", "cacheName") // register KPS Table using its name
	
	// you can also add resource by reflecting static invocables and substitutables from existing class
	builder.reflectClass(PolicyHelper.class)

	// you can also bind extensions to this script
	//builder.attachExtension("java.class.fqdn.as.string")
})

def invoke(msg) {
	// export script context to message, this exposes the following selector expressions
	// groovy.export.selector.name => resolves to value of http.querystring.name
	// groovy.export.policy.healthcheck => invoke this script healthcheck method
	// groovy.export.policy.healthcheck1 => invoke policy using configured espk above
	// groovy.export.policy.healthcheck2 => invoke policy using configured shorthand key above
	// groovy.export.sayHelloWorld => resolves to constant "Hello World !"
	// groovy.export.sayHello(other.expression) => resolves 'other.expression' then format a hello message for the given name
	// groovy.export.RequestURI => returns request URI computed by PolicyHelper class
	msg.put("groovy.export", getExportedResources())

	// invoke local exported method
	return invokeResource(msg, "healthCheck")
}

@InvocableMethod
boolean healthCheck(Message msg) {
	// retrieve request URI from PolicyHelper
	URI uri = substituteResource(msg, "RequestURI");
	
	// log it
	Trace.info(String.format("request URI : %s", uri))
	
	// and invoke locally bound policies
	return invokeResource(msg, "policy.healthcheck1") && invokeResource(msg, "policy.healthcheck2")
}

@SubstitutableMethod
String sayHelloWorld() {
	// simply returns a constant
	return "Hello World !"
}

@ExtensionFunction
String sayHello(Dictionary dict, String name) {
	// format result according to the given name
	return String.format("Hello %s !", name)
}
```
