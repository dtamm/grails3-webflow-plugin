package org.grails.webflow.engine.builder

import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests

class FlowBuilderTransitionCriteriaTests extends AbstractGrailsTagAwareFlowExecutionTests {

    Closure getFlowClosure() {
        return {
            enterPersonalDetails {
                on("submit") { ctx ->
                    error()
                }.to "enterShipping"
                on("another") { ctx ->
                    ctx.flowScope.put("hello", "world")
                }.to "enterShipping"
            }
            enterShipping {
                on("back").to "enterPersonalDetails"
                on("submit").to "displayInvoice"
            }
            displayInvoice()
        }
    }

    void testFlowExecution() {
        startFlow()
        assertCurrentStateEquals "enterPersonalDetails"

        signalEvent("submit")
        assertCurrentStateEquals "enterPersonalDetails"

        signalEvent("another")
        assertCurrentStateEquals "enterShipping"
    }
}
