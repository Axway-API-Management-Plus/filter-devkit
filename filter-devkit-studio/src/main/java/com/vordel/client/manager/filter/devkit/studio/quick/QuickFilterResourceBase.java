package com.vordel.client.manager.filter.devkit.studio.quick;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterSupport;
import com.vordel.client.manager.filter.devkit.runtime.AbstractQuickFilter;
import com.vordel.common.CompositeResourceBundle;
import com.vordel.common.ResourceBase;
import com.vordel.es.EntityType;

public class QuickFilterResourceBase extends ResourceBase {
	private Map<String, ResourceBundle> resourcesMap = new HashMap<String, ResourceBundle>();
	private EntityType definition;

	public QuickFilterResourceBase(EntityType definition, Class<?> clazz, String resourcesFile) {
		super(clazz, resourcesFile);
		
		this.definition = definition;
	}

	public QuickFilterResourceBase(EntityType definition, Class<?> clazz) {
		super(clazz);
		
		this.definition = definition;
	}

	public QuickFilterResourceBase(EntityType definition, String packageName) {
		super(packageName);
		
		this.definition = definition;
	}
	
	
	private PropertyResourceBundle getDefinitionProperties() {
		String value = AbstractQuickFilter.getConstantStringValue(definition, QuickFilterSupport.QUICKFILTER_RESOURCES);
		PropertyResourceBundle resources = null;
		
		if (value != null) {
			try {
				StringReader reader = new StringReader(value);
				
				resources = new PropertyResourceBundle(reader);
			} catch (IOException e) {
				resources = null;
			}
		}
		
		return resources;
	}

	@Override
	public ResourceBundle getBundle(String language) {
		ResourceBundle result = resourcesMap.get(language);
		
		if (result == null) {
			ResourceBundle parent = super.getBundle(language);
			PropertyResourceBundle properties = getDefinitionProperties();

			if (properties != null) {
				CompositeResourceBundle resources = new CompositeResourceBundle();
				
				resources.addBundle(properties);
				resources.addBundle(parent);
				
				result = resources;
			} else {
				result = parent;
			}
			
			resourcesMap.put(language, result);
		}
		
		return result;
	}
}
