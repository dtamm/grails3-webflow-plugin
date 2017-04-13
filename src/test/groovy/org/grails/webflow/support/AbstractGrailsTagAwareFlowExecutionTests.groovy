package org.grails.webflow.support

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.core.GrailsApplication
import grails.web.pages.GroovyPagesUriService
import org.grails.web.beans.PropertyEditorRegistryUtils
import org.grails.web.servlet.DefaultGrailsApplicationAttributes
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.web.pages.DefaultGroovyPagesUriService
import org.grails.web.servlet.context.support.WebRuntimeSpringConfiguration
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.SitemeshLayoutViewResolver
import org.grails.webflow.MockApplicationContext
import org.springframework.beans.PropertyEditorRegistrySupport
import org.springframework.context.support.StaticMessageSource
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.webflow.expression.spel.WebFlowSpringELExpressionParser
import org.springframework.webflow.test.execution.AbstractFlowExecutionTests
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.ServletContext
import org.springframework.webflow.engine.builder.support.FlowBuilderServices
import org.springframework.webflow.mvc.builder.MvcViewFactoryCreator
import org.springframework.binding.convert.service.DefaultConversionService
import org.springframework.webflow.definition.registry.FlowDefinitionRegistryImpl
import org.springframework.webflow.context.ExternalContext
import org.springframework.webflow.context.servlet.ServletExternalContext
import org.springframework.webflow.test.MockExternalContext
import org.springframework.webflow.definition.FlowDefinition
import org.grails.webflow.engine.builder.FlowBuilder
import org.springframework.webflow.engine.builder.FlowAssembler
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry
import org.springframework.webflow.engine.builder.DefaultFlowHolder

/**
 * @author Graeme Rocher
 * @since 0.4
 */
abstract class AbstractGrailsTagAwareFlowExecutionTests extends AbstractFlowExecutionTests {

    ServletContext servletContext
    GrailsWebRequest webRequest
    FlowBuilderServices flowBuilderServices
    FlowDefinitionRegistry flowDefinitionRegistry = new FlowDefinitionRegistryImpl()
    MockHttpServletRequest request
    MockHttpServletResponse response
    def ctx
    def originalHandler
    ApplicationContext appCtx
    GrailsApplication ga
    def mockManager
    def gcl = new GroovyClassLoader()

    GrailsApplication grailsApplication
    StaticMessageSource messageSource

    final void setUp() throws Exception {
        GroovySystem.metaClassRegistry.removeMetaClass(String)

        originalHandler =     GroovySystem.metaClassRegistry.metaClassCreationHandle

        GroovySystem.metaClassRegistry.metaClassCreationHandle = new ExpandoMetaClassCreationHandle()

        grailsApplication = new DefaultGrailsApplication(gcl.loadedClasses, gcl)
        ga = grailsApplication
        grailsApplication.initialise()
        mockManager = new MockGrailsPluginManager(grailsApplication)
        mockManager.registerProvidedArtefacts(grailsApplication)
        onInit()

        def mockControllerClass = gcl.parseClass("class MockController {  def index = {} } ")
        ctx = new MockApplicationContext()

        grailsApplication.setApplicationContext(ctx)

        // Need to setup PropertyEditorRegistry...
        PropertyEditorRegistrySupport propertyEditorRegistry = new PropertyEditorRegistrySupport();
        ctx.registerMockBean("propertyEditorRegistrySupport", propertyEditorRegistry)

        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, grailsApplication)
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, mockControllerClass)

        messageSource = new StaticMessageSource()
        ctx.registerMockBean("manager", mockManager)
        ctx.registerMockBean("messageSource", messageSource)
        ctx.registerMockBean("grailsApplication",grailsApplication)
        ctx.registerMockBean(GroovyPagesUriService.BEAN_ID, new DefaultGroovyPagesUriService())

        def dependantPluginClasses = []
        dependantPluginClasses << gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")
        dependantPluginClasses << gcl.loadClass("org.grails.plugins.CodecsGrailsPlugin")
        dependantPluginClasses << gcl.loadClass("org.grails.plugins.i18n.I18nGrailsPlugin")
        dependantPluginClasses << gcl.loadClass("org.grails.plugins.web.mapping.UrlMappingsGrailsPlugin")
        dependantPluginClasses << gcl.loadClass("org.grails.plugins.web.GroovyPagesGrailsPlugin")

        def dependentPlugins = dependantPluginClasses.collect { new DefaultGrailsPlugin(it, grailsApplication)}

        dependentPlugins.each{ mockManager.registerMockPlugin(it); it.manager = mockManager }
        mockManager.registerProvidedArtefacts(grailsApplication)
        def springConfig = new WebRuntimeSpringConfiguration(ctx)

        servletContext =  ctx.servletContext

        springConfig.servletContext = servletContext

        dependentPlugins*.doWithRuntimeConfiguration(springConfig)

        appCtx = springConfig.getApplicationContext()
        String.metaClass.encodeAsHTML = {-> delegate }

        grailsApplication.mainContext = appCtx

        SitemeshLayoutViewResolver sitemeshLayoutViewResolver = appCtx.getBean('jspViewResolver')
        // TODO: Do this in a nicer way - but this is required otherwise we have issues where we get NPE as the views don't really exist
        sitemeshLayoutViewResolver.innerViewResolver.resolveJspView = true
        //

        flowBuilderServices = new FlowBuilderServices()
        MvcViewFactoryCreator viewCreator = new MvcViewFactoryCreator()
        viewCreator.viewResolvers = [appCtx.getBean('jspViewResolver')]
        viewCreator.applicationContext = appCtx
        flowBuilderServices.viewFactoryCreator = viewCreator
        flowBuilderServices.conversionService = new DefaultConversionService()
        flowBuilderServices.expressionParser = new WebFlowSpringELExpressionParser(new SpelExpressionParser(), flowBuilderServices.conversionService)

        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
        mockManager.applicationContext = appCtx
        servletContext.setAttribute(DefaultGrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx)
        GroovySystem.metaClassRegistry.removeMetaClass(String)
        GroovySystem.metaClassRegistry.removeMetaClass(Object)
       //grailsApplication.tagLibClasses.each { tc -> GroovySystem.metaClassRegistry.removeMetaClass(tc.clazz)}
        mockManager.doDynamicMethods()
        webRequest = GrailsWebMockUtil.bindMockWebRequest(appCtx)
        request = webRequest.currentRequest
        request.characterEncoding = "utf-8"
        response = webRequest.currentResponse

        PropertyEditorRegistryUtils.registerCustomEditors(webRequest, propertyEditorRegistry, Locale.default);
        request.setAttribute(DefaultGrailsApplicationAttributes.PROPERTY_REGISTRY, propertyEditorRegistry);

        assert appCtx.grailsUrlMappingsHolder
    }

    final void tearDown() {
        RequestContextHolder.setRequestAttributes(null)
        GroovySystem.metaClassRegistry.setMetaClassCreationHandle(originalHandler)
        onDestroy()
    }

    protected void startFlow() {
        super.startFlow(new ServletExternalContext(servletContext, request, response))
    }

    protected ExternalContext signalEvent(String eventId) {
        MockExternalContext context = new MockExternalContext()
        context.setNativeRequest request
        context.setNativeResponse response
        context.setNativeContext servletContext
        context.setEventId(eventId)
        resumeFlow(context)
        return context
    }

    FlowDefinition registerFlow(String flowId, Closure flowClosure) {
        FlowBuilder builder = new FlowBuilder(flowId, flowClosure, flowBuilderServices, getFlowDefinitionRegistry())
        builder.viewPath = "/"
        builder.applicationContext = appCtx
        FlowAssembler assembler = new FlowAssembler(builder, builder.getFlowBuilderContext())
        getFlowDefinitionRegistry().registerFlowDefinition(new DefaultFlowHolder(assembler))
        return getFlowDefinitionRegistry().getFlowDefinition(flowId)
    }

    FlowDefinition getFlowDefinition() {
        return registerFlow(getFlowId(), getFlowClosure())
    }

    protected void onInit() {}

    protected void onDestroy() {}

    String getFlowId() { 'testFlow' }

    abstract Closure getFlowClosure()
}
