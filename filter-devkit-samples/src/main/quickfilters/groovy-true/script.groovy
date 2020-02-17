import com.vordel.circuit.CircuitAbortException
import com.vordel.circuit.Message
import com.vordel.config.Circuit
import com.vordel.config.ConfigContext
import com.vordel.es.Entity
import com.vordel.es.EntityStoreException
import com.vordel.trace.Trace

void attach(ConfigContext ctx, Entity entity) throws EntityStoreException {
	Trace.info("Groovy True Filter attached");
}

boolean invoke(Circuit c, Message m) throws CircuitAbortException {
	Trace.info("Groovy True Filter called");
	return true;
}

void detach() {
	Trace.info("Groovy True Filter detached");
}
