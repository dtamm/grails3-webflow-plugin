package org.codehaus.groovy.grails.plugins.web

import org.junit.Ignore
import spock.lang.Specification

abstract class AbstractGrailsPluginTests extends GroovyTestCase {
/* TODO: Make compile under 3.2.x
    ServletContext servletContext
    GrailsWebRequest webRequest
    MockHttpServletRequest request
    MockHttpServletResponse response
    def gcl = new GroovyClassLoader()
    def ga
    def mockManager
    def ctx
    def originalHandler
    def springConfig
    ApplicationContext appCtx
    def pluginsToLoad = []
    def resolver = new PathMatchingResourcePatternResolver()

    protected void onSetUp() {}

    protected final void setUp() {
        super.setUp()

        ExpandoMetaClass.enableGlobally()

        ctx = new MockApplicationContext()
        onSetUp()
        ga = new DefaultGrailsApplication(gcl.getLoadedClasses(),gcl)
        ga.metadata[Metadata.APPLICATION_NAME] = getClass().name
        def mainContext = new MockApplicationContext()
        mainContext.registerMockBean UrlConverter.BEAN_NAME, new CamelCaseUrlConverter()
        ga.mainContext = mainContext
        mockManager = new MockGrailsPluginManager(ga)
        def dependentPlugins = pluginsToLoad.collect { new DefaultGrailsPlugin(it, ga)}
        dependentPlugins.each{ mockManager.registerMockPlugin(it); it.manager = mockManager }
        mockManager.doArtefactConfiguration()
        ga.initialise()
        ga.setApplicationContext(ctx)
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, ga)
//        ctx.registerMockBean(GrailsApplicationPostProcessor, gcl)

        ctx.registerMockBean("manager", mockManager)

        def configurator = new GrailsRuntimeConfigurator(ga, ctx)
        configurator.pluginManager = mockManager
        ctx.registerMockBean(GrailsRuntimeConfigurator.BEAN_ID, configurator)
        def outerContext
        springConfig = new WebRuntimeSpringConfiguration(ctx)
        servletContext = new MockServletContext(new MockResourceLoader())
        springConfig.servletContext = servletContext
        mockManager.registerProvidedArtefacts(ga)
        dependentPlugins*.doWithRuntimeConfiguration(springConfig)
        mockManager.applicationContext = appCtx
        servletContext.setAttribute(DefaultGrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx)
        appCtx = springConfig.getApplicationContext()
        dependentPlugins*.doWithDynamicMethods(appCtx)
        dependentPlugins*.doWithApplicationContext(appCtx)
    }

    protected final void tearDown() {
        pluginsToLoad = []
        ExpandoMetaClass.disableGlobally()
        Holders.setPluginManager null
    }
*/
}
