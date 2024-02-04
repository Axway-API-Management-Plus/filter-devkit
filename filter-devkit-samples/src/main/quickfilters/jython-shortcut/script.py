from com.vordel.trace import Trace
from com.vordel.circuit import CircuitAbortException
from com.vordel.circuit.filter.devkit.context.resources import PolicyResource

def attach(ctx, entity):
    global shortcut
    
    # create the shortcut object
    shortcut = PolicyResource(ctx, entity, "circuitPK")
    Trace.info("Jython Shortcut Filter attached")

def detach():
    global shortcut
    
    # discard the shortcut object
    shortcut = None
    Trace.info("Jython Shortcut Filter detached")

def invoke(circuit, msg):
    global shortcut
    
    # check that the policy is properly configured
    # Should not occur, this is just an example to show how to raise a CircuitAbortException
    if shortcut == None:
        raise CircuitAbortException("No Policy Configured")
    
    # call the policy (CircuitAbortException may occur during this call)
    return shortcut.invoke(circuit, msg)
