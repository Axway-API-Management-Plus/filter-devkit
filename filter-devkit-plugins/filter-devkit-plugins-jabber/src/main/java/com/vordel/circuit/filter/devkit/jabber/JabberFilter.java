package com.vordel.circuit.filter.devkit.jabber;

import java.security.GeneralSecurityException;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.quick.JavaQuickFilterDefinition;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterRequired;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.trace.Trace;

@QuickFilterRequired({ MessageProperties.CONTENT_BODY })
@QuickFilterType(name = "JabberFilter", category = "XMPP Filters", resources = "jabberfilter.properties", page = "jabberpage.xml")
public class JabberFilter extends JavaQuickFilterDefinition {
	private Selector<String> to = null;
	private Selector<String> password = null;
	private Selector<String> from = null;
	private Selector<String> resourceName = null;
	private Selector<String> messageStr = null;

	@QuickFilterField(name = "fromEmailAddress", cardinality = "1", type = "string")
	private void setFromEmailAddress(ConfigContext ctx, Entity entity, String field) {
		from = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "toEmailAddress", cardinality = "1", type = "string")
	private void setToEmailAddress(ConfigContext ctx, Entity entity, String field) {
		to = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "password", cardinality = "1", type = "string")
	private void setPassword(ConfigContext ctx, Entity entity, String field) {
		byte[] passwordBytes = entity.getEncryptedValue(field);

		if (passwordBytes != null) {
			try {
				passwordBytes = ctx.getCipher().decrypt(passwordBytes);

				String pass = new String(passwordBytes);

				password = SelectorResource.fromLiteral(pass, String.class, false);
			} catch (GeneralSecurityException exp) {
				Trace.error(exp);
			}
		}
	}

	@QuickFilterField(name = "resourceName", cardinality = "1", type = "string")
	private void setResourceName(ConfigContext ctx, Entity entity, String field) {
		resourceName = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "messageStr", cardinality = "1", type = "string")
	private void setMessageStr(ConfigContext ctx, Entity entity, String field) {
		messageStr = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@Override
	public boolean invokeFilter(Circuit c, Message message, MessageProcessor p) throws CircuitAbortException {
		XMPPConnection connection = null;
		try {
			ConnectionConfiguration config = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
			connection = new XMPPConnection(config);
			SASLAuthentication.supportSASLMechanism("PLAIN", 0);
			connection.connect();
			connection.login(from.substitute(message), password.substitute(message), resourceName.substitute(message));
		} catch (XMPPException ex) {
			Trace.error("Error establishing connection to XMPP Server");
		}
		Chat chat = connection.getChatManager().createChat(to.substitute(message), new MessageListener() {
			@Override
			public void processMessage(Chat arg0, org.jivesoftware.smack.packet.Message arg1) {
				Trace.debug(arg1.getBody());
			}
		});
		try {
			chat.sendMessage(messageStr.substitute(message));
			connection.disconnect();
		} catch (org.jivesoftware.smack.XMPPException ex) {
			Trace.error("Error Delivering block");
		}
		return true;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		// Nothing here (handled by injected setters)
	}

	@Override
	public void detachFilter() {
		// Nothing to detach
	}

}
