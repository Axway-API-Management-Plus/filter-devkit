# Advanced Script Filter

The Advanced script filter is a feature which extends the classing scripting filter in order to provide the following functionnalitites:
 - Call Policies,
 - Resolve Selectors,
 - Retrieve a reference to a KPS Table,
 - Retrieve a reference to a Cache,
 - Export resources for use in another script (of filter),
 - Attachment hook (before handling messages)
 - Detachment hook (before shutdown or re-deployment)
 - Throw real CircuitAbortException.

Additionally, Grovy script can be reflected to have those additional features :
 - Export Groovy methods as Selectors (like extention plugins),
 - Implement a REST service (using JAX-RS)

To implement this, functions are added to the script at top level. Technically, those functions are closures of the AdvancedScriptRuntime class. All functions except reflective ones are available to all supported scripting languages. Since groovy is close to Java, reflection is available and useable as long as strong typing is used.

## Calling Policies from script

To Call a policy, It muse first be declared in the script resources section (select a policy reference and give it a name, the name is only valid within this context). Once declared, calling the policy is as simple as :

```java
invokeResource(msg, "registered.policy")
```

The above snippet can be inserted anywhere in the script as long as the first argument resolve to the current transaction message.

