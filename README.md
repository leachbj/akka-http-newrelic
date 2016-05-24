akka-http-newrelic
==================

[![Build Status](https://travis-ci.org/leachbj/akka-http-newrelic.png?branch=master)](http://travis-ci.org/leachbj/akka-http-newrelic)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.leachbj/akka-http-newrelic/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.leachbj/akka-http-newrelic)

An extension for New Relic to provide akka-http client tracing with cross application
tracing support.

The extension jar should be included in your `extensions` directory as configured
in the `newrelic.yml` or via the JVM property `newrelic.config.extensions.dir`.
