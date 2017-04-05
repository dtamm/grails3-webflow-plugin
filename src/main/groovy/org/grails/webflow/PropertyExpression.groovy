package org.grails.webflow;

//
// TODO: Re-introduced because the Groovy Equivalent did not seem to be compatible; but I've not investigated much - need to
// take a better look.
//
class PropertyExpression implements Cloneable {

    private StringBuffer propertyExpression

    def getValue() { propertyExpression.toString() }

    PropertyExpression(String initialName) {
        propertyExpression = new StringBuffer(initialName)
    }

    Object getProperty(String name) {
        propertyExpression << ".$name"
        return this
    }
}