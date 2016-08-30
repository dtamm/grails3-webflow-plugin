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

import grails.core.GrailsApplication
import grails.web.mapping.UrlMappingsHolder
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.util.ClassPropertyFetcher;

import javax.servlet.http.HttpServletRequest;

import grails.core.GrailsControllerClass;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
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

public class GrailsFlowHandlerMapping extends UrlMappingsHandlerMapping {
    private GrailsApplication grailsApplication;
    private static final String FLOW_SUFFIX = "Flow";

    GrailsFlowHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        super(urlMappingsHolder)
    }

    protected Object getHandlerForControllerClass(GrailsControllerClass controllerClass, HttpServletRequest request) {
        final String actionName = (String) request.getAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE);
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

        GrailsControllerClass controllerClass = (GrailsControllerClass) grailsApplication.getArtefactForFeature(
                ControllerArtefactHandler.TYPE, uri)

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

    @Override
    public MetaClass getMetaClass() {
        return this.metaClass
    }
}
