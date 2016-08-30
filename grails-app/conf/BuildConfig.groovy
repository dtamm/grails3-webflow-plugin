if(System.getenv('TRAVIS_BRANCH')) {
    grails.project.repos.grailsCentral.username = System.getenv("GRAILS_CENTRAL_USERNAME")
    grails.project.repos.grailsCentral.password = System.getenv("GRAILS_CENTRAL_PASSWORD")    
}

grails.project.work.dir = 'target'

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {

    inherits "global"
    log "warn"

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        compile 'org.grails:grails-web-databinding-spring:2.4.3'
        compile('org.springframework.webflow:spring-webflow:2.4.1.RELEASE') {
            exclude group:"org.springframework", name:"spring-beans"
            exclude group:"org.springframework", name:"spring-expression"
            exclude group:"org.springframework", name:"spring-context"
            exclude group:"org.springframework", name:"spring-core"
            exclude group:"org.springframework", name:"spring-web"
            exclude group:"org.springframework", name:"spring-webmvc"
            exclude group:"commons-logging", name:"commons-logging"
        }  

    }

    plugins {
        optional ":hibernate4:4.3.5.3"
        build(":release:3.0.1", ':rest-client-builder:2.0.1') {
            export = false
        }
    }
}
