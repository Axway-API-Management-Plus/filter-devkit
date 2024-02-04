# Dynamic Compiler

The Dynamic Compiler package  feature allows to compile Java classes on the fly during deployment. Compiled classes are included in modules and plugins discovery. The class loader of compiled classes is isolated and is not accessible from API Gateway ecosystem except from registered filters, modules and plugins. 

Usage of the dynamic compiler feature will never ever be supported in a production system. It's purpose is to speed up development cycle locally. Since compilation is done during deployment, there is no need to restart API Gateway when code is modified. A Deployment is sufficient.

The Dynamic compiler is implemented as a extension module and has the highest priority (last attached module). Compiled classes and modules will get registered within Dynamic Compiler Attachment.

## Compiled directories

The following selectors are used to locate classes to be compiled:
 - ${environment.VDISTDIR}/ext/dynamic : Global compiled classes from API Gateway installation
 - ${environment.VINSTDIR}/ext/dynamic : Instance specific Compilation directory

Compiled classes of each directory are isolated, you can't access Global compiled classes from Instance specific classes.

## Compiled Modules and Plugins

Classes which inherits ExtensionModule and annotated with @ExtensionPlugin are registered as [pluggable modules](ClassPathScanning.md)
