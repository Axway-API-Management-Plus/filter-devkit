# Filter Development Kit

This project contains a set of libraries for implementing API Gateway Filters and making advanced scripts.

## Prerequisites

To build this artefact, you need the following items:
 - JDK 11
 - API Gateway and Policy Studio 7.7.20240228
 
Code base is actually stable and has been tested from 20220530 (JDK 8) to 20240228 (JDK 11). Even if jars compiled using another release should work, it is recommended to use artifacts produced for a particular release using the branch specific the the API Gateway version you're targeting (some bytecode changes can trigger a MethodNotFoundException). If no branch already exists please open an issue for it or make a Pull Request for this new branch.

A playground Dockerfile is provided. It provides an on-premise API Gateway, Policy Studio, In-Browser GUI and IDE with the FDK pre-installed.

The Filter Devkit can be installed in two ways :
 - Full feature set by importing the runtime typeset ([ExtensionLoader](filter-devkit-runtime/src/main/java/com/vordel/circuit/filter/devkit/context/ExtensionLoader.java) loadable module and [Advanced script](docs/AdvancedScriptFilter.md) custom filter,
 - Groovy Script only [context](docs/ScriptContext.md) and [extensions](docs/ScriptExtensions.md)

## Basic Features

 - [Filter Code Generator (Java Quick Filters)](docs/QuickJavaFilter.md)
 - [Advanced script filter](docs/AdvancedScriptFilter.md)
 - [Extension subsystem](docs/Extensions.md) ([child first ClassLoader](docs/ChildFirstClassLoader.md), selector accessible methods and script extensions)

## Developer Feature

 - [Dynamic compilation support](filter-devkit-dynamic/README.md)

## Using without modifying the EntityStore

It's possible to use the Filter DevKit without importing the base typeset. In this case the following feature are available 

 - [Script context](docs/ScriptContext.md) for groovies (bring resource binding to default script filter)
 - [Script extensions](docs/ScriptExtensions.md) load mechanism within script context creation (limited to groovy scripts)
 - Static only reflection of [extension context](docs/ExtensionContext.md) within script context creation.

Additionally, generated filters are independent base extensions. So it is safe to import and use [generated filters](docs/QuickJavaFilter.md) without importing and using the Filter DevKit  typeset.

## Building and Installing

A [QuickStart](docs/QuickStart.md) procedure is provided by a Docker Image
Otherwise, you can follow the [Build and Install instructions](docs/BuildAndInstall.md).

YAML Entity Store is actually not supported.

Happy testing !