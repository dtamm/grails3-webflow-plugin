/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.webflow

import grails.core.GrailsControllerClass
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.web.UrlConverter
import org.grails.core.util.ClassPropertyFetcher
import org.grails.webflow.context.servlet.GrailsFlowUrlHandler
import org.grails.webflow.engine.builder.FlowBuilder
import org.grails.webflow.execution.GrailsFlowExecutorImpl
import org.grails.webflow.mvc.servlet.GrailsFlowHandlerAdapter
import org.grails.webflow.mvc.servlet.GrailsFlowHandlerMapping
import org.grails.webflow.scope.ScopeRegistrar
import org.springframework.context.ApplicationContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.webflow.conversation.impl.SessionBindingConversationManager
import org.springframework.webflow.core.collection.LocalAttributeMap
import org.springframework.webflow.core.collection.MutableAttributeMap
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry
import org.springframework.webflow.definition.registry.FlowDefinitionRegistryImpl
import org.springframework.webflow.engine.RequestControlContext
import org.springframework.webflow.engine.builder.DefaultFlowHolder
import org.springframework.webflow.engine.builder.FlowAssembler
import org.springframework.webflow.engine.builder.support.FlowBuilderServices
import org.springframework.webflow.engine.impl.FlowExecutionImplFactory
import org.springframework.webflow.execution.FlowExecutionFactory
import org.springframework.webflow.execution.repository.impl.DefaultFlowExecutionRepository
import org.springframework.webflow.execution.repository.snapshot.SerializedFlowExecutionSnapshotFactory
import org.springframework.webflow.expression.spel.WebFlowSpringELExpressionParser
import org.springframework.webflow.mvc.builder.MvcViewFactoryCreator
import org.springframework.binding.convert.service.DefaultConversionService
import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Provides the core Webflow functionality within Grails.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
class WebFlowPluginSupport {

    static doWithSpring = {

        // TODO: Remove - HACK :P - issue is that GrailsControllerClass assumes that ...Flow Closures are actions,
        ///      and therefore uses the name of the closure as the default action for the controller in links; this is
        //       a temporary hack to workaround this - but not sure how to solve this permanently. :(
        requestStateLookupStrategy(FlowAwareDefaultRequestStateLookupStrategy)
        //

        // Keep this private else it causes issues....
        DefaultConversionService conversionServiceRef = new DefaultConversionService()
        //

        viewFactoryCreator(MvcViewFactoryCreator) {
            viewResolvers = ref('jspViewResolver')
        }

        flowHandlerMapping(GrailsFlowHandlerMapping, ref("grailsUrlMappingsHolder")) {
            // run at slightly higher precedence
            order = Integer.MAX_VALUE - 1
        }
        //conversationService(WebflowDefaultConversionService)
        sep(SpelExpressionParser)
        expressionParser(WebFlowSpringELExpressionParser, sep, conversionServiceRef)

        flowBuilderServices(FlowBuilderServices) {
            conversionService = conversionServiceRef
            expressionParser =  expressionParser
            viewFactoryCreator = viewFactoryCreator
        }

        flowRegistry(FlowDefinitionRegistryImpl)

        flowScopeRegistrar(ScopeRegistrar)

       // TODO: Was springConfig.containsBean("sessionFactory") but this seems to cause issues under 3.2.x, so
       //       temporarily changed - need to check this actually works as currently untested with databases
       boolean configureHibernateListener = true
       if (configureHibernateListener)  {
            try {
                webFlowHibernateConversationListener(org.grails.webflow.persistence.SessionAwareHibernateFlowExecutionListener, ref("sessionFactory"), ref("transactionManager"))
                webFlowExecutionListenerLoader(org.springframework.webflow.execution.factory.StaticFlowExecutionListenerLoader, webFlowHibernateConversationListener)
            }
            catch (MissingPropertyException mpe) {
                // no session factory, this is ok
                log.info "Webflow loading without Hibernate integration. SessionFactory not found."
                configureHibernateListener = false
            }
        }

        flowExecutionFactory(FlowExecutionImplFactory) {
            executionAttributes = new LocalAttributeMap(alwaysRedirectOnPause:true)
            if (configureHibernateListener) {
                executionListenerLoader = ref("webFlowExecutionListenerLoader")
            }
        }

        conversationManager(SessionBindingConversationManager)
        flowExecutionSnapshotFactory(SerializedFlowExecutionSnapshotFactory, flowExecutionFactory, flowRegistry)
        flowExecutionRepository(DefaultFlowExecutionRepository, conversationManager, flowExecutionSnapshotFactory)
        flowExecutor(GrailsFlowExecutorImpl, flowRegistry, flowExecutionFactory, flowExecutionRepository)

        mainFlowController(GrailsFlowHandlerAdapter) {
            flowExecutor = flowExecutor
            flowUrlHandler = { GrailsFlowUrlHandler uh -> }
        }
    }

    static doWithApplicationContext = { ApplicationContext appCtx ->
        FlowExecutionFactory flowExecutionFactory = appCtx.getBean("flowExecutionFactory")
        flowExecutionFactory.executionKeyFactory = appCtx.getBean("flowExecutionRepository")
    }

    //
    // TODO: This is a hack at present - need to know how to do this properly
    //
    static doWithDynamicMethods = { appCtx, application ->

        // Manually wire this... :P
        FlowAwareDefaultRequestStateLookupStrategy lookupStrategy = appCtx.getBean(FlowAwareDefaultRequestStateLookupStrategy)
        lookupStrategy.grailsApplication = application
        GrailsFlowHandlerMapping grailsFlowHandlerMapping = appCtx.getBean(GrailsFlowHandlerMapping)
        grailsFlowHandlerMapping.grailsApplication = application
        UrlConverter urlConverter = appCtx.getBean(UrlConverter)

        // Find instances of ...Flow closures in the controller
        for (GrailsControllerClass c in application.controllerClasses) {
            registerFlowsForController(appCtx, c)
        }

        RequestControlContext.metaClass.getFlow = { -> delegate.flowScope }

        RequestControlContext.metaClass.getConversation = { -> delegate.conversationScope }

        RequestControlContext.metaClass.getFlash = { -> delegate.flashScope }

        MutableAttributeMap.metaClass.getProperty = { String name ->
            def mp = delegate.class.metaClass.getMetaProperty(name)
            def result = null
            if (mp) result = mp.getProperty(delegate)
            else {
                result = delegate.get(name)
            }
            result
        }
        MutableAttributeMap.metaClass.setProperty = { String name, value ->
            def mp = delegate.class.metaClass.getMetaProperty(name)
            if (mp) mp.setProperty(delegate, value)
            else {
                delegate.put(name, value)
            }
        }
        MutableAttributeMap.metaClass.clear = {-> delegate.asMap().clear() }
        MutableAttributeMap.metaClass.getAt = { String key -> delegate.get(key) }
        MutableAttributeMap.metaClass.putAt = { String key, value -> delegate.put(key,value) }
    }

    private static void registerFlowsForController(appCtx, GrailsControllerClass c) {
        GrailsFlowHandlerMapping grailsFlowHandlerMapping = appCtx.getBean(GrailsFlowHandlerMapping)
        UrlConverter urlConverter = appCtx.getBean(UrlConverter)

        // Clear any old mappings...
        grailsFlowHandlerMapping.clearFlowMappingsForController(c)

        Map<String, Closure> flows = [:]
        final String FLOW_SUFFIX = "Flow"
        ClassPropertyFetcher classPropertyFetcher = ClassPropertyFetcher.forClass(c.clazz)
        for (PropertyDescriptor propertyDescriptor : classPropertyFetcher.getPropertyDescriptors()) {
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null && !Modifier.isStatic(readMethod.getModifiers())) {
                final Class<?> propertyType = propertyDescriptor.getPropertyType();
                if ((propertyType == Object.class || propertyType == Closure.class) && propertyDescriptor.getName().endsWith(FLOW_SUFFIX)) {
                    String closureName = propertyDescriptor.getName();
                    flows.put(closureName, getPropertyValue(classPropertyFetcher, closureName, Closure.class, c.clazz));
                }
            }
        }
        // Register the flows...
        for (flow in flows) {
            String flowName = flow.key.substring(0, flow.key.length() - FLOW_SUFFIX.length());
            def flowId = ("${c.logicalPropertyName}/" + flowName).toString()
            def builder = new FlowBuilder(flowId, flow.value, appCtx.flowBuilderServices, appCtx.flowRegistry)
            builder.viewPath = "/"
            builder.applicationContext = appCtx
            def assembler = new FlowAssembler(builder, builder.getFlowBuilderContext())
            appCtx.flowRegistry.registerFlowDefinition new DefaultFlowHolder(assembler)
            String controllerPath = "/" + urlConverter.toUrlElement(c.name) + "/";
            String tmpUri = controllerPath + urlConverter.toUrlElement(flowName);
            String tmpUri2 = tmpUri + "/" + "**";
            String viewPath = "/" + GrailsNameUtils.getPropertyNameRepresentation(c.name) + "/" + flowName;
            grailsFlowHandlerMapping.registerFlowMappingPattern(tmpUri, c)
            grailsFlowHandlerMapping.registerFlowMappingPattern(tmpUri2, c)
        }
    }

    public static <T,X> T getPropertyValue(ClassPropertyFetcher classPropertyFetcher, String propName, Class<T> type, Class<X> queriedType) {
        T value = classPropertyFetcher.getPropertyValue(propName, type);
        if (value == null) {
            // Groovy workaround
            return getGroovyProperty(classPropertyFetcher, propName, type, false, queriedType);
        }
        return returnOnlyIfInstanceOf(value, type);
    }

    private static <T,X> T getGroovyProperty(ClassPropertyFetcher classPropertyFetcher, String propName, Class<T> type, boolean onlyStatic, Class<X> queriedType) {
        Object value = null;
        if (GroovyObject.class.isAssignableFrom(queriedType)) {
            MetaProperty metaProperty = getMetaClass().getMetaProperty(propName);
            if (metaProperty != null) {
                int modifiers = metaProperty.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    value = metaProperty.getProperty(queriedType);
                }
                else if (!onlyStatic) {
                    value = metaProperty.getProperty(getReferenceInstance(classPropertyFetcher));
                }
            }
        }
        return returnOnlyIfInstanceOf(value, type);
    }

    private static Object getReferenceInstance(ClassPropertyFetcher classPropertyFetcher) {
        Object obj = classPropertyFetcher.getReference();
        if (obj instanceof GroovyObject) {
            ((GroovyObject)obj).setMetaClass(getMetaClass());
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static <T> T returnOnlyIfInstanceOf(Object value, Class<T> type) {
        if ((value != null) && (type==Object.class || GrailsClassUtils.isGroovyAssignableFrom(type, value.getClass()))) {
            return (T)value;
        }

        return null;
    }

    // TODO: Untested - may not work...
    static onChange = { event, application ->
        ApplicationContext appCtx = event.ctx
        FlowDefinitionRegistry flowRegistry = appCtx.flowRegistry
        GrailsControllerClass controller = application.getControllerClass(event.source.name)
        if (!controller) {
            return
        }

        def controllerClass = controller.clazz
        def registry = GroovySystem.metaClassRegistry
        def currentMetaClass = registry.getMetaClass(controllerClass)

        try {
            // we remove the current meta class because webflow needs an unmodified (via meta programming) controller
            // in order to configure itself correctly
            registry.removeMetaClass controllerClass
            controller.getReference().getWrappedInstance().metaClass = registry.getMetaClass(controllerClass)
            registerFlowsForController(appCtx, controllerClass)
        }
        finally {
            registry.setMetaClass controllerClass, currentMetaClass
            controller.getReference().getWrappedInstance().metaClass = currentMetaClass
        }
    }
}
