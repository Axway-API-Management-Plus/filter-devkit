# Quick Java Filter Tutorial

## 0.Prerequisites

To complete this tutorial you must have the following artifacts installed either in the instance ext/lib directory, or in the global ext/lib directory
 - filter-devkit-dynamic-*.jar (dynamic compilation support for instance)
 - filter-devkit-runtime-*.jar (basic runtime)

As an option, you can install filter-devkit-advanced-runtime-*.jar if the artifact is compatible with your environment.

Since you're developing a Java filter, it may be useful to enable Java debug port on your development instance (so you can set breackpoints), ensure the jvm.xml file contains the following declaration (you can change the port 9777 with another value):

```xml
<ConfigurationFragment>
        <VMArg name="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=9777"/>
</ConfigurationFragment>
```

## 1.Setup QuickJavaFilter typeset generator (once)

Making a Quick Java Filter requires a developer gateway to generate filter schema. Schema filter generation can either be done from the filter 'Scripting Context Call' (available in the advanced maven module) or from the [following groovy script](../filter-devkit-samples/src/main/groovy/com/vordel/sdk/samples/quick/TypesetGenerator.groovy) :

```groovy
package com.vordel.sdk.samples.quick

import com.vordel.circuit.Message
import com.vordel.circuit.script.bind.ExtensionContext
import com.vordel.circuit.script.context.MessageContextModule
import com.vordel.circuit.script.context.MessageContextTracker
import com.vordel.config.Circuit

import groovy.transform.Field

/**
 * Dynamic compiler exposed invocables and selectors
 */
@Field private static final ExtensionContext DYNAMIC_COMPILER = MessageContextModule.getGlobalResources("dynamic.compiler");

boolean invoke(Message msg) {
	/* retrieve current circuit for this message */
	MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(msg);
	Circuit circuit = tracker.getCircuit();

	/* incoke the dynamic compiler service */
	return DYNAMIC_COMPILER.invoke(circuit, msg, "TypeSetService");
}
```

[this policy](policies/TypeSetGenerator-script.xml) contains the groovy script definition. Import this policy if you do not have the advanced package installed or if you do not wish to use the 'Scripting Context Call' filter.

[this policy](policies/TypeSetGenerator-script.xml) contains the 'Scripting Context Call' definition.

As a result for this step, you should have a '/typeset' path defined on the default services. This path will generate a zip archive of discovered filters.

The typeset policy expose the following services

 - /typeset : generate typeset from dynamic compiler and current classpath
 - /typeset/dynamic : generate typeset from dynamic compiler only
 - /typeset/static : generate typeset from current classpath only

Additionally the 'type' query parameter can be user to filter requested filter types. If you wish  to generate typeset for multiple filter types, you can use the 'type' parameter multiple times.

## 2.Create dynamic compiled directory

In order to prototype your filter and to avoid full API Gateway restart, create either the global or the instance dynamic compiled directory

 - ${environment.VDISTDIR}/ext/dynamic
 - ${environment.VINSTDIR}/ext/dynamic

As a result for this step, you should have at least one dynamic compiled directory

## 3.Create Skeleton Java class

Create a class which extends 'QuickJavaFilterDefinition' in a dynamic compiled directory

```java
package com.vordel.sdk.samples.tutorial;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

public class QuickJavaTutorial extends QuickJavaFilterDefinition {
	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		// TODO Auto-generated method stub
	}

	@Override
	public void detachFilter() {
		// TODO Auto-generated method stub
	}
}
```

_Note_ : respect java package directory otherwise, the runtime may not locate required properties and ui files.

## 4.Create UI File and properties in the same package of the Java class

create tutorial.xml file in the same directory (package) your java class is:

```xml
<ui>
	<panel columns="2">
		<panel columns="2" fill="false">
			<!-- filter name attribute -->
			<NameAttribute />
		</panel>

		<panel columns="2" span="2" fill="false">
			<ReferenceSelector
				field="circuitPK"
				selectableTypes="FilterCircuit"
				searches="ROOT_CIRCUIT_CONTAINER,CircuitContainer"
				label="TUTORIALSHORTCUT_LABEL"
				title="TUTORIALSHORTCUT_TITLE" />
		</panel>
	</panel>
</ui>
```

create tutorial.properties file in the same directory (package) your java class is:

```properties
#
# name that will be displayed in the Filter header. This property is mandatory
#
FILTER_DISPLAYNAME=Java Shortcut filter
#
# Description that will be displayed in the filter header. This property is mandatory
#
FILTER_DESCRIPTION=A class which calls a policy reference
#
# Filter Palette Category. This property is optional and defaults to 'Utility'
#
FILTER_CATEGORY=Utility
#
# Filter Palette Icon This property is optional and defaults to 'filter_small'
#
FILTER_ICON=filter_small

TUTORIALSHORTCUT_LABEL=Policy Reference
TUTORIALSHORTCUT_TITLE=Select a Policy
```

At the end of this step, you have the base Filter class with ui and properties available.

## 5.Add the Annotations to your filter

Add the QuickFilterType on top of your class definition, and needed configuration setters

```java
package com.vordel.sdk.samples.tutorial;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

@QuickFilterType(name = "QuickJavaFilterTutorial", resources = "tutorial.properties", ui = "tutorial.xml")
public class QuickJavaTutorial extends QuickJavaFilterDefinition {
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

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/*
		 * The main attach call. This method is called after all individual setters has
		 * been called
		 */
	}

	@Override
	public void detachFilter() {
		policy = null;
	}
}
```

Once you're done with the filter class, deploy configuration. You may have errors with the unused field 'policy'. Those errors can be safely ignored for the moment (If you do not want errors, you have to code the filter invoke method).

Invoking the typeset service with the 'type' parameter equals to 'QuickJavaFilterTutorial' should now return a typeset archive with your filter definition. Import the inflated files in the policy studio to see your filter in the palette. Loop over this step until you are done with the UI and fields.

DO NOT persist a filter instance in the configuration until you're updating the schema using annotated configuration setters. If you need to update UI or properties and you have existing filter instances, ensure your modifications are backward compatible or you will break your deployment.

## 6.Code the filter invoke function

Once the schema and the UI is ready, you can code your filter.

```java
package com.vordel.sdk.samples.tutorial;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

@QuickFilterType(name = "QuickJavaFilterTutorial", resources = "tutorial.properties", ui = "tutorial.xml")
public class QuickJavaTutorial extends QuickJavaFilterDefinition {
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

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		if (policy == null) {
			throw new CircuitAbortException("No Policy Configured");
		}

		return policy.invoke(c, m);
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/*
		 * The main attach call. This method is called after all individual setters has
		 * been called
		 */
	}

	@Override
	public void detachFilter() {
		policy = null;
	}
}
```

Each time the configuration is deployed, the class is recompiled (since it is in a dynamic compiled directory). To test any change in your filter call a policy which uses it. A deployment must be done each time the code is modified. You do not need to import typeset if you do not modify UI or schema.

## 6.Package the filter

Once you're done with all above steps, your filter is finished. You now have to package it in a jar file (all needed class files you've created for it, properties and UI). Add the packaged jar into the required ext/lib directory (instance or global).

_Note_ : Once your filter is packaged, remove all packaged filter from the dynamic compiled directory.

