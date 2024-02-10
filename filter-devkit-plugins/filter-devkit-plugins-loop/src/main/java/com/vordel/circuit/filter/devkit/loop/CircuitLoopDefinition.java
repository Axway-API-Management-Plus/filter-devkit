package com.vordel.circuit.filter.devkit.loop;

import javax.el.ELException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.context.resources.PolicyResource;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.quick.JavaQuickFilterDefinition;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;

@QuickFilterType(name = "CircuitLoopFilter", category = "Utility", icon="circuit_shortcut", resources = "circuit_loop.properties", page = "circuit_loop.xml")
public class CircuitLoopDefinition extends JavaQuickFilterDefinition {
	public static final int LOOPTYPE_WHILE = 1;
	public static final int LOOPTYPE_DOWHILE = 2;

	private Selector<Integer> loopType = null;
	private Selector<Boolean> loopCondition = null;
	private Selector<Integer> loopMax = null;
	private Selector<Integer> loopTimeout = null;

	private Selector<Boolean> loopErrorCircuit = null;
	private Selector<Boolean> loopErrorCondition = null;
	private Selector<Boolean> loopErrorMax = null;
	private Selector<Boolean> loopErrorTimeout = null;
	private Selector<Boolean> loopErrorEmpty = null;

	private PolicyResource loopCircuit = null;

	@QuickFilterField(name = "loopType", cardinality = "1", type = "integer", defaults = "1")
	private void setLoopType(ConfigContext ctx, Entity entity, String field) {
		this.loopType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "loopCondition", cardinality = "1", type = "string", defaults = "${1 + 1 == 2}")
	private void setLoopCondition(ConfigContext ctx, Entity entity, String field) {
		this.loopCondition = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "loopCircuit", cardinality = "?", type = "@FilterCircuit")
	private void setLoopCircuit(ConfigContext ctx, Entity entity, String field) {
		ESPK delegatedPK = entity.getReferenceValue(field);
		DelayedESPK loopReference = new DelayedESPK(delegatedPK);
		ESPK loopPK = loopReference.substitute(Dictionary.empty);

		/*
		 * Ensure we have a configured circuit and we do not loop on our parent policy
		 */
		if ((!EntityStore.ES_NULL_PK.equals(loopPK) && (!entity.getParentPK().equals(loopPK)))) {
			this.loopCircuit = new PolicyResource(ctx, delegatedPK);
		}
	}

	@QuickFilterField(name = "loopMax", cardinality = "1", type = "integer", defaults = "10")
	private void setLoopMax(ConfigContext ctx, Entity entity, String field) {
		this.loopMax = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "loopTimeout", cardinality = "1", type = "integer", defaults = "10000")
	private void setLoopTimeout(ConfigContext ctx, Entity entity, String field) {
		this.loopTimeout = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "loopErrorCircuit", cardinality = "1", type = "boolean", defaults = "1")
	private void setErrorCircuit(ConfigContext ctx, Entity entity, String field) {
		this.loopErrorCircuit = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "loopErrorCondition", cardinality = "1", type = "boolean", defaults = "0")
	private void setErrorCondition(ConfigContext ctx, Entity entity, String field) {
		this.loopErrorCondition = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "loopErrorMax", cardinality = "1", type = "boolean", defaults = "0")
	private void setErrorMax(ConfigContext ctx, Entity entity, String field) {
		this.loopErrorMax = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "loopErrorTimeout", cardinality = "1", type = "boolean", defaults = "0")
	private void setErrorTimeout(ConfigContext ctx, Entity entity, String field) {
		this.loopErrorTimeout = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "loopErrorEmpty", cardinality = "1", type = "boolean", defaults = "0")
	private void setErrorEmpty(ConfigContext ctx, Entity entity, String field) {
		this.loopErrorEmpty = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
	}

	@Override
	public void detachFilter() {
	}

	private static <T> T evalSelector(Message m, Selector<T> attribute) throws CircuitAbortException {
		T result = null;

		try {
			result = attribute.substitute(m, true);
		} catch (ELException e) {
			/* examine cause */
			Throwable cause = e.getCause();

			if (cause instanceof CircuitAbortException) {
				/* This is a CircuitAbortException, relay it */
				throw (CircuitAbortException) cause;
			}

			throw new CircuitAbortException(String.format("Could not evaluate expression %s", attribute.getLiteral()), e);
		} catch (Exception e) {
			throw new CircuitAbortException(String.format("Could not evaluate expression %s", attribute.getLiteral()), e);
		}

		return result;
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		Integer type = evalSelector(m, loopType);

		long start = System.currentTimeMillis();
		long timeout = timeout(m);

		if (type == null) {
			throw new CircuitAbortException("Unable to compute loop type " + loopType.getLiteral());
		}

		boolean result = true;
		boolean loop = false;
		int max = max(m);
		int count = 0;

		/*
		 * difference between while and do/while loops is only for the first round
		 */
		switch (type) {
		case LOOPTYPE_WHILE:
			/* check condition before first round */
			loop = condition(m);

			if (!loop) {
				/* we are going to skip the loop, check for error condition */
				result = !isErrorCondition(m, loopErrorEmpty);
			}
			break;
		case LOOPTYPE_DOWHILE:
			/* check condition after first round */
			loop = true;
			break;
		default:
			throw new CircuitAbortException("Wrong loop type '" + type + "' (only 1 and 2 permitted)");
		}

		while (loop) {
			/* execute loop (and exit if the loop circuit return false) */
			m.put("loopCount", count);

			loop = executeLoop(c, m);
			count++;
			if (!loop) {
				/*
				 * loop circuit did return an error, check for error condition
				 */
				result = !isErrorCondition(m, loopErrorCircuit);
			} else if (timeout > 0) {
				/* Check if the current loop has expired */
				loop = System.currentTimeMillis() <= (start + timeout);

				if (!loop) {
					/*
					 * expiration time has exhausted, check for error condition
					 */
					result = !isErrorCondition(m, loopErrorTimeout);
				} else if (max > 0) {

					/* Check if the maximum iteration count has been reached */
					loop = count < max;

					if (!loop) {
						/*
						 * max iteration count was exhausted, check for error condition
						 */
						result = !isErrorCondition(m, loopErrorMax);
					} else {
						/* check if we need more iteration */
						loop = condition(m);

						if (!loop) {
							/* not looping anymore, check for error condition */
							result = !isErrorCondition(m, loopErrorCondition);
						}
					}
				}
			}
		}

		return result;
	}

	private boolean isErrorCondition(Message m, Selector<Boolean> attribute) throws CircuitAbortException {
		Boolean result = evalSelector(m, attribute);

		if (result == null) {
			throw new CircuitAbortException("Could not evaluate boolean expression " + attribute.getLiteral());
		}

		return result;
	}

	private boolean condition(Message m) throws CircuitAbortException {
		Boolean result = evalSelector(m, loopCondition);

		if (result == null) {
			throw new CircuitAbortException("Could not evaluate boolean expression " + loopCondition.getLiteral());
		}

		return result;
	}

	private int max(Message m) throws CircuitAbortException {
		Integer result = evalSelector(m, loopMax);

		if (result == null) {
			throw new CircuitAbortException("Could not evaluate maximum loop iteration count " + loopMax.getLiteral());
		}

		if (result < 0) {
			throw new CircuitAbortException("Can't have negative maximum iteration count");
		}

		return result;
	}

	private int timeout(Message m) throws CircuitAbortException {
		Integer result = evalSelector(m, loopTimeout);

		if (result == null) {
			throw new CircuitAbortException("Could not evaluate loop timeout " + loopMax.getLiteral());
		}

		if (result < 0) {
			throw new CircuitAbortException("Can't have negative maximum loop timeout");
		}

		return result;
	}

	private boolean executeLoop(Circuit c, Message m) throws CircuitAbortException {
		return loopCircuit == null ? false : loopCircuit.invoke(m);
	}
}
