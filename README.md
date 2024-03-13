# Filter Development Kit

This project contains a set of libraries for implementing API Gateway Filters and making advanced scripts.

## Prerequisites

To build this artefact, you need the following items:
 - JDK 11
 - API Gateway and Policy Studio 7.7.20240228
 
Code base is actually stable and has been tested from 20220530 (JDK 8) to 20240228 (JDK 11). Even if jars compiled using another release should work, it is recommended to use artifacts produced for a particular release using the branch specific the the API Gateway version you're targeting (some bytecode changes can trigger a MethodNotFoundException). If no branch already exists please open an issue for it or make a Pull Request for this new branch.

A playground Dockerfile is provided. It provides an on-premise API Gateway, Policy Studio, In-Browser GUI and IDE with the FDK pre-installed.

## Basic Features

 - [Filter Code Generator (Java Quick Filters)](docs/QuickJavaFilter.md)
 - [Advanced script filter](docs/AdvancedScriptFilter.md)
 - [Extension subsystem](docs/Extensions.md) (child first ClassLoader, selector accessible methods and script extensions)

## Developer Feature

 - [Dynamic compilation support](docs/DynamicCompiler.md)

## Building and Installing

A [QuickStart](docs/QuickStart.md) procedure is provided by a Docker Image
Otherwise, you can follow the [Build and Install instructions](docs/BuildAndInstall.md).

YAML Entity Store is actually not supported.

Happy testing !