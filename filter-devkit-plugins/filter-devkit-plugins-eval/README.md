# Extended Eval Selector Filter

This project is an implementation of the Eval Selector filter, adding an Exception relaying feature.

## Basic Features

Compatible with the Eval Selector original filter except that it can relay CircuitAbortExceptions thrown by underlying selector evaluation.

## Prerequisites

The Filter DevKit runtime must be deployed on the API Gateway and in the policy studio

## Installation

Copy the produced jar in the API Gateway /ext/lib directory (can be used either in the global /ext/lib directory or in the instance private directory), and restart the target instance.

Copy the produced jar in the Policy Studio plugins directory and restart the Policy Studio with the '-clean' flag.

In the produced jar there is a typeset directory which must be extracted and imported in the target configuration using the Policy Studio 'Import Custom Filter' feature.
