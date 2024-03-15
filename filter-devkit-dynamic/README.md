# Dynamic Compiler

The Dynamic Compiler package  feature allows to compile Java classes on the fly during deployment. Compiled classes are included in modules and plugins discovery. The class loader of compiled classes is isolated and is not accessible from API Gateway ecosystem except from modules, plugins and script extensions.

It's purpose is to speed up development cycle locally. Since compilation is done during deployment, there is no need to restart API Gateway when code is modified. A Deployment is sufficient to apply changes.

I can also be used to use Java classes instead of scripts. Using Java classes has many advantages against scripts, starting with strong typing system.

The Dynamic compiler is implemented as a extension module and has the highest priority (last attached module). Compiled classes and modules will get registered within Dynamic Compiler Attachment. It also uses the [child first ClassLoader](../docs/ChildFirstClassLoader.md) to isolate compiler and annotation processor from the API Gateway main class path. Compiled classes are also isolated from compiler class path.

## Compiled directories

The following selectors are used to locate classes to be compiled:
 - ${environment.VDISTDIR}/ext/dynamic : Global sources compilation directory from API Gateway installation
 - ${environment.VINSTDIR}/ext/dynamic : Instance specific sources compilation directory

Compiler additional class path is located in the directoty ${environment.VDISTDIR}/ext/extra/compiler

Compiled classes and generated resources are put in the following Directories :
 - ${environment.VDISTDIR}/ext/extra/compiled : Global compiled classes from API Gateway installation
 - ${environment.VINSTDIR}/ext/extra/compiled : Instance specific complied classes

Those 2 lasts directories are handled by the module (removed on startup/shutdown or non existent source directory, automatic creation)
 
Global compiled classes are seen by instance classes.

## Compiled Modules and Plugins

All compiled classes are scanned by the annotation processor and registered like normal classes by the [Extension](../docs/Extensions.md) process. All Extensions features are available except for the [child first ClassLoader](../docs/ChildFirstClassLoader.md) (since the dynamic compiler doesn't know the external class path at compile time).

## Manual installation

Those steps are not needed if using the deployRuntime gradle task (as it takes care to deploy everything at the right place).

 - create the directory ${environment.VDISTDIR}/ext/extra/compiler and copy the ecj compiler jar and the annotation processor (filter-devkit-tools) into the created directory,
 - copy the dynamic plugin (filter-devkit-dynamic) into the ${environment.VDISTDIR}/ext/lib directory
 - create the ${environment.VDISTDIR}/ext/dynamic to enable compilation for all instances
 - for each instance create the ${environment.VINSTDIR}/ext/dynamic to enable compilation on specific instance (class path is inherited from global compilation above).

