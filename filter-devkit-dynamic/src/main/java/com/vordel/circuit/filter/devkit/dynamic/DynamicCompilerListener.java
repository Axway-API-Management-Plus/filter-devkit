package com.vordel.circuit.filter.devkit.dynamic;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic.Kind;

import com.vordel.trace.Trace;

public final class DynamicCompilerListener implements DiagnosticListener<JavaFileObject> {
	@Override
	public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
		if (diagnostic != null) {
			Kind kind = diagnostic.getKind();
			JavaFileObject source = diagnostic.getSource();
			String message = diagnostic.getMessage(null);

			switch (kind) {
			case ERROR:
			case WARNING:
			case MANDATORY_WARNING:
				/* report all warnings as errors */
				Trace.error(String.format("%s: %s line %d", source.getName(), kind.name(), diagnostic.getLineNumber()));
				Trace.error(message);
				break;
			case NOTE:
			case OTHER:
				Trace.info(message);
				break;
			default:
				break;
			}
		}
	}
}