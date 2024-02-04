package com.vordel.circuit.filter.devkit.samples.quick

import com.vordel.circuit.Message
import com.vordel.circuit.filter.devkit.context.ExtensionContext
import com.vordel.circuit.filter.devkit.context.ExtensionLoader
import com.vordel.circuit.filter.devkit.context.MessageContextTracker
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
