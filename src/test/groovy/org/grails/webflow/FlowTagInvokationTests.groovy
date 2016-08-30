package org.grails.webflow

import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class FlowTagInvokationTests extends AbstractGrailsTagAwareFlowExecutionTests {

    void testRegularTagInvokation() {
        request[GrailsApplicationAttributes.CONTROLLER] = ga.getControllerClass("TestController").newInstance()

        String.metaClass.encodeAsHTML = {-> delegate }

        startFlow()
        signalEvent('two')

        def model = getFlowScope()

        assertEquals '<a href="/foo/bar?execution=1"></a>',model.theLink
    }

    void testNamespacedTagInvokation() {

        String.metaClass.encodeAsHTML = {-> delegate }

        startFlow()
        signalEvent('three')

        def model = getFlowScope()
        assertEquals '<a href="/foo/bar?execution=1"></a>',model.theLink
    }

    void onInit() {
        def controllerClass = gcl.parseClass('''
class TestController {
    def index = {}
}
        ''')
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass)
    }

    Closure getFlowClosure() {
        return {
            one {
                on('two') {
                    [theLink:link(controller:"foo", action:"bar")?.toString()]
                }.to 'two'
                on('three') {
                    [theLink:g.link(controller:"foo", action:"bar")?.toString()]
                }.to 'three'
            }
            two {
                on('success').to 'end'
            }
            three {
                on('success').to 'end'
            }
            end()
        }
    }
}
