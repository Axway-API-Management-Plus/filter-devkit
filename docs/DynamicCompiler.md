# Dynamic Compiler

The Dynamic Compiler package  feature allows to compile Java classes on the fly during deployment. Compiled classes are included in modules, plugins and quick java filters discovery. The class loader of compiled classes is isolated and is not accessible from API Gateway ecosystem except from registered filters, modules and plugins. 

Usage of the dynamic compiler feature will never ever be supported in a production system. It's purpose is to speed up development cycle locally. Since compilation is done during deployment, there is no need to restart API Gateway when code is modified. A Deployment is sufficient.

The Dynamic compiler is implemented as a extension module and has the highest priority (last attached module). Compiled classes and modules will get registered within Dynamic Compiler Attachment.

## Compiled directories

The following selectors are used to locate classes to be compiled:
 - ${environment.VDISTDIR}/ext/dynamic : Global compiled classes from API Gateway installation
 - ${environment.VINSTDIR}/ext/dynamic : Instance specific Compilation directory

Compiled classes of each directory are isolated, you can't access Global compiled classes from Instance specific classes.

## Compiled Modules and Plugins

Classes which inherits ExtensionModule and annotated with @ExtensionPlugin are registered as [pluggable modules](ClassPathScanning.md)

## Compiled Quick Java Filters

Quick Filter types get registered when the class is elligible to be used as a [Quick Java Filter](QuickJavaFilter.md).
 
## Exposed services and methods

The Dynamic Compiler is the main development tool to setup Quick Java Filters. The module is implemented as a plugin which actually exposes 4 invocable methods under the 'dynamic.compiler' extension:

 - GlobalTypeSet : set a zip archive as message body which contains typeset and typedoc for all Quick Java Filters available in the classpath including dynamically compiled classes
 - DynamicTypeSet : set a zip archive as message body which contains typeset and typedoc for all Quick Java Filters available in dynamically compiled classes
 - StaticTypeSet : set a zip archive as message body which contains typeset and typedoc for all Quick Java Filters available in the classpath but not in dynamically compiled classes
 - TypeSetService : REST API which returns a zip archive as message body which contains typeset and typedoc according to provided parameters.

Those exposed service allows to develop on a Quick Java Filter without restarting API Gateway or Policy Studio. Each time Filter schema is modified, calling the typeset service (exposed in a policy) will return modified typesets for the policy studio. Redeploying the configuration from the Policy Studio will update the filter class.
