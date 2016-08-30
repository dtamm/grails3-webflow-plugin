package org.grails.webflow.engine.builder

import grails.util.GrailsWebMockUtil
import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests

class FlowBuilderDecisionExecutionTests extends AbstractGrailsTagAwareFlowExecutionTests{

    Closure getFlowClosure() {
        return {
            displaySearchForm {
                on("submit").to "executeSearch"
            }
            executeSearch {
                action {
                    def r = searchService.executeSearch(params.q)
                    def result = success(results:r)
                    r ? result : none()
                }
                on("success").to "displayResults"
                on("none").to "noResults"
                on("error").to "displaySearchForm"
            }
            noResults()
            displayResults()
        }
    }

    def searchService = [executeSearch:{["foo", "bar"]}]
    def params = [q:"foo"]

    void testNoResultFlowExecution() {
        GrailsWebMockUtil.bindMockWebRequest()
        searchService = [executeSearch:{[]}]

        startFlow()
        assertCurrentStateEquals "displaySearchForm"

        signalEvent("submit")
        assertFlowExecutionEnded()
        assertFlowExecutionOutcomeEquals "noResults"
    }

    void testSuccessFlowExecution() {
        GrailsWebMockUtil.bindMockWebRequest()
        searchService = [executeSearch:{["foo", "bar"]}]

        startFlow()
        assertCurrentStateEquals "displaySearchForm"

        signalEvent("submit")
        assertFlowExecutionEnded()
        assertFlowExecutionOutcomeEquals "displayResults"
    }
}
