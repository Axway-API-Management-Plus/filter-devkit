var Trace = Java.type('com.vordel.trace.Trace');

function attach(ctx, entity) {
	Trace.info("Javascript True Filter attached");
}

function invoke(circuit, msg) {
	Trace.info("Javascript True Filter called");

	return true;
}

function detach() {
	Trace.info("Javascript True Filter detached");
}
