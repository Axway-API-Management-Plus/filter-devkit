# Common FDK Use Cases

## Custom Filter development and deployment

## Calling Policies

Calling policies can be done using regular scripts. It however involve harcoding the policy reference into the script. This way, policy studio doesn't know the reference to the target invokable which can't be renamed without breaking the calling script (also exporting the script won't export the called policy). Maintenance of regular scripts calling policy is also complicated by the fact that following/retrieving references to a policy is not available.

## Reuse scripts instead of Cut'n'Paste

It is common in the API Gateway to Cut'n'Paste snippets of script. When using groovy script, it is now possible to export commonly used functions to the message and call them later from another scripts and/or selectors.

## Export Self contained artifacts

When using regular scripts KPS and Caches can be accessed using aliases coded as string. This way of scripting prevent the policy studio of collecting references to target objects. By using reference configuration to KPS tables, Caches and policies, exporting a script also export the target objects.

## Interface with large libraries with conflicting classes

Conflicting classes is a common problem in the Java Ecosystem which is not handled by the API Gateway extension mechanism. As a consequence, adding a library with conflicting dependencies can lead to undefined behavior (API Gateway not starting, corrupted messages processing, etc...).

## Use Java as a scripting language

The Dynamic compiler offers a strong typing language which can be used directly to implement script like functionality in the API Gateway.

## Implement real local services

API Gateway is weak when it comes to implement local services. The best alternative would be to use the existing Servlet interface which is outdated and does not have access to policies. Using the JAX-RS runtime plugin allows policy developers to implement real local services while still using policies.
