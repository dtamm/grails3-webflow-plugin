package org.grails.webflow.engine.builder

import grails.util.GrailsWebMockUtil;

import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests

class FlowBuilderExecutionTests extends AbstractGrailsTagAwareFlowExecutionTests {

    Closure getFlowClosure() {
        return {
            displaySearchForm {
                on("submit").to "executeSearch"
            }
            executeSearch {
                action {
                    [results:searchService.executeSearch(params.q)]
                }
                on("success").to "displayResults"
                on("error").to "displaySearchForm"
                on(FooException).to "errorView"
            }
            displayResults {
                on("return").to "displaySearchForm"
            }
            errorView()
        }
    }

    def searchService = [executeSearch:{["foo", "bar"]}]
    def params = [q:"foo"]

    void testFlowExecution() {
        GrailsWebMockUtil.bindMockWebRequest()
        startFlow()
        assertCurrentStateEquals "displaySearchForm"

        signalEvent("submit")
        assertCurrentStateEquals "displayResults"

        def model = getFlowScope()
        assertEquals(["foo", "bar"],model.results)

        signalEvent("return")
        assertCurrentStateEquals "displaySearchForm"
    }

    void testFlowExecutionException() {
        GrailsWebMockUtil.bindMockWebRequest()
        searchService.executeSearch = { throw new FooException() }

        startFlow()
        assertCurrentStateEquals "displaySearchForm"

        signalEvent("submit")
        assertFlowExecutionEnded()
        assertFlowExecutionOutcomeEquals "errorView"
    }
}

class FooException extends Exception {}
