# Quick Java Filters

The Quick Java Filter feature allow to package a Java classes as first order filters (i.e appears in pallette etc). Filter classes and typesets are generated from a single annotated definition class. All generated classes and typesets are packaged with the declarative UI and NLS properties files to for an policy studio compatible plugin.

## Quick Java Filters annotations

To be recognized by the annotation processor. The definition class must inherit [JavaQuickFilterDefinition](../filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/quick/JavaQuickFilterDefinition.java) and must be annotated with [QuickFilterType](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterType.java).

Each filter field must have an specific setter method annotated either with [QuickFilterComponent](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterComponent.java) or [QuickFilterField](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterField.java).

Annotated components setter must have the follolwing arguments :
 - ConfigContext object (first argument of attachFilter method)
 - Entity object (second argument of attachFilter)
 - Collection of ESPK object, each representing an entity component

Annotated fields must have the following arguments : (ConfigContext ctx, Entity entity, String field)
 - ConfigContext object (first argument of attachFilter method)
 - Entity object (second argument of attachFilter)
 - Entity field name as string

Annotated setter must always be present (it is used for typeset/typedoc generation).If the filter requires complex initialization with optional fields or components, let the setter empty and use the filter definition attach hook (which is called after all setters) to execute missing or additional configuration.

Additionally, three other annotations can be added to the definition type ([QuickFilterRequired](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterRequired.java), [QuickFilterGenerated](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterGenerated.java) and [QuickFilterConsumed](../filter-devkit-annotations/src/main/java/com/vordel/circuit/filter/devkit/quick/annotations/QuickFilterConsumed.java)). Those last annotations are used to indicate required/generated/consumed attributes to Policy Studio.

## Quick Filter Lifecycle

Quick Filters are no longer discovered dynamically. They are instantiated using the regular API Gateway and Policy Studio mechanism. On the API Gateway side, the generated filter class will return the generated MessageProcessor class which will instantiate and initialize the filter definition (MessageProcessor is acting as a proxy class for the definition).

Each Quick Java Filter initialize itself using the following steps:
 - invoke the no-arg constructor,
 - call each annotated setter from entity fields with proper field name or ESPK collection,
 - call the attachFilter() method

the method invokeFilter() will be called for all incoming messages which will reach the filter

Lastly, when detached, the detachFilter() is called. Any error occuring while detach is logged but ignored

## Quick Filter Packaging

All quick filter files are generated during compilation, including typeset, OSGi packaging and eclipse plugin xml file. Source files are generated from simple templates which does not require any dependency outside JDK.

The [Extended Eval Filter](../filter-devkit-plugins/filter-devkit-plugins-eval/README.md) is a good starting point for developing new quick filters since it include a minimal set of what is required in a definition and build system.
