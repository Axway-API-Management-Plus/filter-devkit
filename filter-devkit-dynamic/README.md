# Dynamic Compiler

The Dynamic Compiler package  feature allows to compile Java classes on the fly during deployment. Compiled classes are included in modules and plugins discovery. The class loader of compiled classes is isolated and is not accessible from API Gateway ecosystem except from registered filters, modules and plugins.

It's purpose is to speed up development cycle locally. Since compilation is done during deployment, there is no need to restart API Gateway when code is modified. A Deployment is sufficient to apply changes.

The Dynamic compiler is implemented as a extension module and has the highest priority (last attached module). Compiled classes and modules will get registered within Dynamic Compiler Attachment. It also uses the 'child first' class loader to isolate compiler and annotation processor from the API Gateway main class path. Compiled classes are also isolated from compiler class path.

## Compiled directories

The following selectors are used to locate classes to be compiled:
 - ${environment.VDISTDIR}/ext/dynamic : Global sources compilation directory from API Gateway installation
 - ${environment.VINSTDIR}/ext/dynamic : Instance specific sources compilation directory

Compiler additional class path is located in the directoty ${environment.VINSTDIR}/ext/extra/compiler

Compiled classes and generated resources are put in the following Directories :
 - ${environment.VDISTDIR}/ext/extra/compiled : Global compiled classes from API Gateway installation
 - ${environment.VINSTDIR}/ext/extra/compiled : Instance specific complied classes

Those 2 lasts directories are handled by the module (removed on startup/shutdown or non existent source directory, automatic creation)
 
Global compiled classes are seen by instance classes.

## Compiled Modules and Plugins

All compiled classes are scanned by the annotation processor and registered like normal classes by the [Extension](../docs/Extensions.md) process. All Extensions features including 'child first' class loader are available.
