# Quick Java Filters

The Quick Java Filter feature allow to package a Java classes as first order filters (i.e appears in pallette etc). It works using class path scanning and annotations.

## Differences with [Quick Script Filters](QuickScriptFilter.md)

The typedoc.xml is generated from class methods annotations. The generated EntityType does not embed script and engineName attributes. All other features and limitations apply.

## Required Packaging

Typical Filter packaging is done with a class which extends the QuickJavaFilterDefinition class annotated with @QuickFilterType annotation.

Additionally Each Entity field must have a corresponding setter annotated with @QuickFilterField

### QuickFilterType annotation

```java
package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterType {
	/**
	 * @return filter type name in the entity store
	 */
	String name();

	/**
	 * @return filter version
	 */
	int version() default 1;

	/**
	 * @return name of java resource which contains the declarative ui file
	 */
	String ui();

	/**
	 * @return name of java resource which contains the filter properties
	 */
	String resources();
	
	/**
	 * @return extends clause for entity type
	 */
	String extend() default "Filter";
}
```

### QuickJavaFilterDefinition abstract class

```java
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

public abstract class QuickJavaFilterDefinition {
	public abstract boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException;
	
	public abstract void attachFilter(ConfigContext ctx, Entity entity);
	public abstract void detachFilter();
}
```

### QuickFilterField annotation

```java
package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterField {
	/**
	 * @return name of the entity field
	 */
	String name();

	/**
	 * @return mapping of 'cardinality' attribute of field element
	 */
	String cardinality();

	/**
	 * @return mapping of 'type' attribute of field element
	 */
	String type();

	/**
	 * @return mapping of 'isKey' attribute of field element
	 */
	boolean isKey() default false;

	/**
	 * @return array of default values. A signel value will be set in the 'default' field attribute.
	 */
	String[] defaults() default {};
}
```

## Filter Lifecycle

Once a filter has been discovered by [class path scanning](ClassPathScanning.md) or [dynamic compiler](DynamicCompiler.md), the class definition is registered in the MessageContextModule.

After all modules has been attached to the API Gateway, the policy configuration starts. For each Quick Java Filter which is referenced, the dedicated processor will lookup the definition from the MessageContextModule.

Each Quick Java Filter initialize itself using the following steps:
 - invoke the no-arg constructor,
 - call each annotated setter from entity fields,
 - call the attachFilter() method

the method invokeFilter() will be called for all incoming messages which reach the filter

Lastly, when detached, the detachFilter() is called. (Quick Java Filter registry is cleared each time a configuration is deployed).

## Quick Filter Builder Services

No external tool is provided for typedoc creation. This task is done by using a policy which call appropriate method in the [dynamic compiler](DynamicCompiler.md) module. Filter definition for the policy studio must be downloaded from a live API Gateway (typically a development instance).

## Example Policy Shortcut using Quick Java Filter

```java
package com.vordel.sdk.samples.quick;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.trace.Trace;

@QuickFilterType(name = "JavaQuickFilter", resources = "shortcut.properties", ui = "shortcut.xml")
public class QuickFilterJavaProto extends QuickJavaFilterDefinition {
	private Selector<String> hello = null;

	private PolicyResource policy = null;

	/**
	 * This method is called when the filter is attached. The annotation is
	 * reflected in typedoc generation. individual setters are called BEFORE the
	 * attach call.
	 * 
	 * @param ctx    current config context (same as the attach call)
	 * @param entity filter instance (same as the attach call)
	 * @param field  name of the field to be set
	 */
	@QuickFilterField(name = "circuitPK", cardinality = "?", type = "^FilterCircuit")
	private void setShortcut(ConfigContext ctx, Entity entity, String field) {
		policy  = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "message", cardinality = "?", type = "string")
	private void setHelloQuickFilter(ConfigContext ctx, Entity entity, String field) {
		hello = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/*
		 * The main attach call. This method is called after all individual setters has
		 * been called
		 */
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		if (hello != null) {
			String value = hello.substitute(m);
			
			if ((value != null) && (!value.isEmpty())) {
				Trace.info(value);
			}
		}
		
		if (policy == null) {
			throw new CircuitAbortException("No Policy Configured");
		}

		return policy.invoke(c, m);
	}

	@Override
	public void detachFilter() {
		/* regular detach call, no additional processing is done */
		policy = null;
		hello = null;
	}
}
```