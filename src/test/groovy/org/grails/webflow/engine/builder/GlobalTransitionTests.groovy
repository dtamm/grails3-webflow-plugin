package org.grails.webflow.engine.builder

import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests

class GlobalTransitionTests extends AbstractGrailsTagAwareFlowExecutionTests {

    Closure getFlowClosure() {
        return {

            globalTransitions {
                on("globalEnterPersonalDetails").to("enterPersonalDetails")
                on("globalEnterShipping").to("enterShipping")
            }

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

        signalEvent("globalEnterPersonalDetails")
        assertCurrentStateEquals "enterPersonalDetails"

        signalEvent("globalEnterShipping")
        assertCurrentStateEquals "enterShipping"

        signalEvent("globalEnterPersonalDetails")
        assertCurrentStateEquals "enterPersonalDetails"

    }

}
