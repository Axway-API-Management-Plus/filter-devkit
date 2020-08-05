# Groovy Script Filter

The Groovy script filter is a feature which extends the classing scripting filter in order to provide the following functionnalitites:
 - Call Policies,
 - Bind a reference to a KPS Table
 - Export Groovy methods as Selectors,
 - Access to OAuth store,
 - Share methods for usage in other scripts
 - Implement a REST service (using JAX-RS)

Also If you're working with an external IDE you can also provide the script name so you can step debug instead of relying on Traces.

This filter also define the concept of script kind. There are actually 3 kinds of script:
 - Scripts wich does not need to be executed, they are only sharing their methods or ound resources (selectors, policies, KPS, etc...)
 - Scripts which are executed. such scripts contains an 'invoke' method. Parameters are dynamically resolved according to declared arguments.
 - Scripts which implements REST service. shuch scripts does not have 'invoke', they do however have annotated methods which are invoked according to message current state.

Methods MUST be declared with static types, using the 'def' keyword will not work. This requirement is due to injection framework which lookup for types to insert the right object in arguments.

## Calling Policies from script

## Binding KPS Tables
