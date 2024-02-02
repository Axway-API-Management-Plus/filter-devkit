package com.vordel.sdk.samples.quick

import com.vordel.circuit.Message
import com.vordel.circuit.script.bind.ExtensionContext
import com.vordel.circuit.script.context.MessageContextModule
import com.vordel.circuit.script.context.MessageContextTracker
import com.vordel.config.Circuit

import groovy.transform.Field

/**
 * Dynamic compiler exposed invocables and selectors
 */
@Field private static final ExtensionContext DYNAMIC_COMPILER = MessageContextModule.getGlobalResources("dynamic.compiler");

boolean invoke(Message msg) {
	/* incoke the dynamic compiler service */
	return DYNAMIC_COMPILER.invoke(msg, "TypeSetService");
}
