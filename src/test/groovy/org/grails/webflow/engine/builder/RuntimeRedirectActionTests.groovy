package org.grails.webflow.engine.builder

import grails.util.GrailsWebMockUtil
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.webflow.PropertyExpression
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.webflow.test.MockExternalContext
import org.springframework.webflow.test.MockRequestContext

class RuntimeRedirectActionTests extends GroovyTestCase{

    void testRedirectWithPropertyExpression() {
        GrailsWebMockUtil.bindMockWebRequest()

        try {
            def action   = new RuntimeRedirectAction()
            action.controller = "book"
            action.action = "show"
            action.params = [id: new PropertyExpression("flow.id")]
            action.urlMapper = new DefaultUrlMappingsHolder([])
            def ext = new MockExternalContext()
            def context = new MockRequestContext()
            context.setExternalContext(ext)
            def flowScope = context.getFlowScope()
            flowScope.put("id", "1")
            action.execute(context)
            assert "contextRelative:/book/show/1" == ext.getExternalRedirectUrl()

            flowScope = context.getFlowScope()
            flowScope.put("id", "2")
            action.execute(context)
            assert "contextRelative:/book/show/2" == ext.getExternalRedirectUrl()
        }
        finally {
            RequestContextHolder.setRequestAttributes null
        }
    }
}
