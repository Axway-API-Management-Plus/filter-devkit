# Script context

[ScriptContext](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/script/ScriptContext.java) is a feature dedicated to be used in default API Gateway script filter. It's aimed for configurations which can't use the base typeset of the Filter Devkit.

It brings to existing groovies the reflection capabilities of [extension context](ExtensionContext.md). It works by adding a top level declaration to the script with a callback groovy closure.

As Policy Studio references are not available, resource binding is done with text constant references. When using this feature, annotations and runtime libraries must be added to the Policy Studio runtime dependencies.

```groovy
import com.vordel.circuit.Message
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod
import com.vordel.circuit.filter.devkit.script.ScriptContextBuilder

// binds base script runtime methods into groovy script
// also reflect groovy script exportable methods
ScriptContextBuilder.bindGroovyScriptContext(this, { builder ->
	// binds 3 resources to this script
	builder.attachSelectorResourceByExpression("name", "http.querystring.name", String.class)
	builder.attachPolicyByPortableESPK("policy.healthcheck1", "<key type='CircuitContainer'><id field='name' value='Policy Library'/><key type='FilterCircuit'><id field='name' value='Health Check'/></key></key>")
	builder.attachPolicyByShorthandKey("policy.healthcheck2", "/[CircuitContainer]name=Policy Library/[FilterCircuit]name=Health Check")
})

def invoke(msg) {
	// export script context to message
	msg.put("groovy.export", getExportedResources())
	
	// invoke local exported method
	return invokeResource(msg, "healthCheck")
}

@InvocableMethod
boolean healthCheck(Message msg) {
	// invoke locally bound methods
	return invokeResource(msg, "policy.healthcheck1") && invokeResource(msg, "policy.healthcheck2")
}
```
