/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.webflow.mvc.servlet

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.web.mapping.UrlMappingsHolder
import groovy.transform.CompileStatic
import org.grails.core.AbstractGrailsClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.util.ClassPropertyFetcher
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.util.AntPathMatcher;

import javax.servlet.http.HttpServletRequest;

import grails.core.GrailsControllerClass;
import org.grails.web.servlet.DefaultGrailsApplicationAttributes;
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.webflow.mvc.servlet.AbstractFlowHandler

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier;

/**
 * A HandlerMapping implementation that maps Grails controller classes onto flows.
 *
 * @author Graeme Rocher
 * @since 1.2
 */

public class GrailsFlowHandlerMapping extends UrlMappingsHandlerMapping implements InitializingBean {
    private GrailsApplication grailsApplication;
    private static final String FLOW_SUFFIX = "Flow";
    private WebFlowControllerUriCache webFlowControllerUriCache

    GrailsFlowHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        super(urlMappingsHolder)
    }

    protected Object getHandlerForControllerClass(GrailsControllerClass controllerClass, HttpServletRequest request) {
        final String actionName = (String) request.getAttribute(DefaultGrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE);
        if (controllerClass != null && actionName != null) {
            if (isFlowAction(controllerClass)) {
                final String flowid = controllerClass.getLogicalPropertyName() + "/" + actionName;
                return new AbstractFlowHandler() {
                    @Override
                    public String getFlowId() {
                        return flowid;
                    }
                };
            }
        }
        return null;
    }

    protected boolean isFlowAction(GrailsControllerClass controllerClass) {

        for (PropertyDescriptor propertyDescriptor : ClassPropertyFetcher.forClass(controllerClass.clazz).getPropertyDescriptors() ) {
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null && !Modifier.isStatic(readMethod.getModifiers())) {
                final Class<?> propertyType = propertyDescriptor.getPropertyType();
                if ((propertyType == Object.class || propertyType == Closure.class) && propertyDescriptor.getName().endsWith(FLOW_SUFFIX)) {
                    return true
                }
            }
        }
        return false
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        String uri = urlHelper.getPathWithinApplication(request)

        if (logger.isDebugEnabled()) {
            logger.debug("Looking up Grails controller for URI ["+uri+"]");
        }

        GrailsControllerClass controllerClass = webFlowControllerUriCache.getControllerForUri(uri)
        return getHandlerForControllerClass(controllerClass, request)
    }

    @Override
    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
                (HandlerExecutionChain) handler : new HandlerExecutionChain(handler));



        return chain;
    }

    @Override
    public Object invokeMethod(String string, Object object) {
        return object;
    }

    public void registerFlowMappingPattern(String uri, GrailsControllerClass controllerClass) {
        webFlowControllerUriCache.registerFlowUriPattern(uri, controllerClass)
    }

    void clearFlowMappingsForController(GrailsControllerClass controllerClass) {
        webFlowControllerUriCache.clearFlowMappingsForController(controllerClass)
    }

    public void afterPropertiesSet() {
        webFlowControllerUriCache = new WebFlowControllerUriCache(grailsApplication, 10000)
    }

    GrailsApplication getGrailsApplication() {
        return grailsApplication
    }

    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
        this.webFlowControllerUriCache?.grailsApplication = grailsApplication
    }
}

@CompileStatic
class WebFlowControllerUriCache {

    private static final GrailsClass NO_CONTROLLER = new AbstractGrailsClass(Object.class, "Controller") {};
    private GrailsApplication grailsApplication
    private Map<String, List<String>> flowUrisForControllers = [:]
    private ConcurrentLinkedHashMap<ControllerCacheKey, GrailsClass> uriToControllerClassCache;
    private AntPathMatcher pathMatcher = new AntPathMatcher();

    WebFlowControllerUriCache(GrailsApplication grailsApplication, int cacheSize) {
        this.grailsApplication = grailsApplication
        uriToControllerClassCache = new ConcurrentLinkedHashMap.Builder<ControllerCacheKey, GrailsClass>()
                                    .initialCapacity(500)
                                    .maximumWeightedCapacity(new Integer("" + cacheSize))
                                    .build();
    }

    void clearFlowMappingsForController(GrailsControllerClass controllerClass) {
        flowUrisForControllers.remove(controllerClass.name)
        uriToControllerClassCache.clear()
    }

    void registerFlowUriPattern(String flowUri, GrailsControllerClass controllerClass) {
        List<String> paths = flowUrisForControllers.get(controllerClass.name)
        if(paths == null) {
            paths = []
            flowUrisForControllers.put(controllerClass.name, paths)
        }
        paths << flowUri
    }

    @SuppressWarnings("rawtypes")
    public GrailsClass getControllerForUri(Object featureId) {
        String uri;
        String pluginName = null;
        String namespace = null;

        ControllerCacheKey cacheKey;
        if (featureId instanceof ControllerCacheKey) {
            cacheKey = (ControllerCacheKey) featureId;
            pluginName = cacheKey.plugin;
            namespace = cacheKey.namespace;
            uri = cacheKey.uri;
        } else {
            uri = featureId.toString();
            cacheKey = new ControllerCacheKey(uri, null, null);
        }

        GrailsClass controllerClass = uriToControllerClassCache.get(cacheKey);
        if (controllerClass == null) {
            final ApplicationContext mainContext = grailsApplication.getMainContext();
            GrailsPluginManager grailsPluginManager = null;
            if (mainContext.containsBean(GrailsPluginManager.BEAN_NAME)) {
                final Object pluginManagerBean = mainContext.getBean(GrailsPluginManager.BEAN_NAME);
                if (pluginManagerBean instanceof GrailsPluginManager) {
                    grailsPluginManager = (GrailsPluginManager) pluginManagerBean;
                }
            }
            final GrailsClass[] controllerClasses = grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)
            // iterate in reverse in order to pick up application classes first
            for (int i = (controllerClasses.length - 1); i >= 0; i--) {
                boolean matchFound = false
                GrailsClass c = controllerClasses[i];
                List<String> flowUris = flowUrisForControllers.get(c.name)
                if(flowUris?.size() > 0) {
                    String uriFound = flowUris.find {String uriPattern -> pathMatcher.match(uriPattern, (String)featureId) }
                    matchFound = uriFound != null
                }
                if (matchFound) {
                    boolean pluginMatchesFlag = false;
                    boolean namespaceMatchesFlag = false;

                    namespaceMatchesFlag = namespaceMatches((GrailsControllerClass) c, namespace);

                    if (namespaceMatchesFlag) {
                        pluginMatchesFlag = pluginMatches(c, pluginName, grailsPluginManager);
                    }

                    boolean foundController = pluginMatchesFlag && namespaceMatchesFlag;
                    if (foundController) {
                        controllerClass = c;
                        break;
                    }
                }
            }
            if (controllerClass == null) {
                controllerClass = NO_CONTROLLER;
            }

            // don't cache for dev environment
            if (Environment.getCurrent() != Environment.DEVELOPMENT) {
                uriToControllerClassCache.put(cacheKey, controllerClass);
            }
        }

        if (controllerClass == NO_CONTROLLER) {
            controllerClass = null;
        }
        return controllerClass;
    }

    /**
     * @param c the class to inspect
     * @param namespace a controller namespace
     * @return true if c is in namespace
     */
    protected boolean namespaceMatches(GrailsControllerClass c, String namespace) {
        boolean namespaceMatches;
        if (namespace != null) {
            namespaceMatches = namespace.equals(c.getNamespace());
        } else {
            namespaceMatches = (c.getNamespace() == null);
        }
        return namespaceMatches;
    }

    /**
     *
     * @param c the class to inspect
     * @param pluginName the name of a plugin
     * @param grailsPluginManager the plugin manager
     * @return true if c is provided by a plugin with the name pluginName or if pluginName is null, otherwise false
     */
    protected boolean pluginMatches(GrailsClass c, String pluginName, GrailsPluginManager grailsPluginManager) {
        boolean pluginMatches = false;
        if (pluginName != null && grailsPluginManager != null) {
            final GrailsPlugin pluginForClass = grailsPluginManager.getPluginForClass(c.getClazz());
            if (pluginForClass != null && pluginName.equals(pluginForClass.getName())) {
                pluginMatches = true;
            }
        } else {
            pluginMatches = true;
        }
        return pluginMatches;
    }

    GrailsApplication getGrailsApplication() {
        return grailsApplication
    }

    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

}

@CompileStatic
class ControllerCacheKey {
    private final String uri;
    private final String plugin;
    private final String namespace;

    public ControllerCacheKey(String uri, String plugin, String namespace) {
        this.uri = uri;
        this.plugin = plugin;
        this.namespace = namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this.is(o)) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ControllerCacheKey that = (ControllerCacheKey) o;

        if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null) {
            return false;
        }
        if (plugin != null ? !plugin.equals(that.plugin) : that.plugin != null) {
            return false;
        }
        if (!uri.equals(that.uri)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (plugin != null ? plugin.hashCode() : 0);
        result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
        return result;
    }

    String getUri() {
        return uri
    }

    String getPlugin() {
        return plugin
    }

    String getNamespace() {
        return namespace
    }
}
