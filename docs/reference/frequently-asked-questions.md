---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/faq.html
---

# Frequently asked questions [faq]


## How does the agent work? [faq-how-does-it-work]

The agent auto-instruments known frameworks and libraries and records interesting events, like HTTP requests and database queries. To do this, it leverages the capability of the JVM to instrument the bytecode of classes. This means that for the supported technologies, there are no code changes required.

The agent automatically safely injects small pieces of code before and after interesting events to measure their duration and metadata (like the DB statement) as well as HTTP related information (like the URL, parameters, and headers).

For example, if the agent detects that a class extending `javax.servlet.HttpServlet` is loaded, it injects monitoring code before and after the servlet invocation.

These events, called Transactions and Spans, are sent to the APM Server which converts them to a format suitable for Elasticsearch, and sends them to an Elasticsearch cluster. You can then use the APM app in Kibana to gain insight into latency issues and error culprits within your application.


## Is the agent doing bytecode instrumentation? [faq-bytecode-instrumentation]

Yes


## How safe is bytecode instrumentation? [faq-bytecode-instrumentation-safety]

Elastic APM is using the popular bytecode instrumentation library [Byte Buddy](http://bytebuddy.net:), which takes care of the heavy lifting of dealing with actual bytecode and lets us write the instrumentation in pure Java.

Byte Buddy is widely used in popular Open Source projects, for example, Hibernate, Jackson, Mockito and is also commonly used by APM vendors. It is created by a Java Champion, awarded with the Dukes Choice award and currently downloaded over 75 million times a year.

Unlike other bytecode instrumentation libraries, Byte Buddy is designed so that it is impossible to corrupt the bytecode of instrumented classes. It also respects other agents attached to your application at the same time.


## Do I need to re-compile my application? [faq-recompile]

No


## What is the recommended sample rate? [recommended-sample-rate]

There is no one-size-fits-all answer to an ideal sample rate. Sampling comes down to your preferences and your application. The more you want to sample, the more network bandwidth and disk space you’ll need.

It’s important to note that the latency of an application won’t be affected much by the agent (in the order of single-digit microseconds), even if you sample at 100%. However, the background reporter thread has some work to do for serializing and gzipping events. If your application is not CPU bound, this shouldn’t matter much. Note that if the APM Server can’t handle all the events, the agent will drop data to not crash your application. It will then also not serialize and gzip the events.

Sample rate can be changed by altering the [`transaction_sample_rate` (performance)](/reference/config-core.md#config-transaction-sample-rate) configuration.


## Is there recommended RAM when using APM? [recommended-ram]

No. The Java agent is designed to be very light on memory. It has a static overhead of only a couple MBs.


## What if the agent doesn’t support the technologies I’m using? [faq-unsupported-technologies]

You can use the [public API](/reference/public-api.md) to create custom spans and transactions, the [plugin API](/reference/plugin-api.md) to create custom instrumentation, participate in the [survey](https://docs.google.com/forms/d/e/1FAIpQLScd0RYiwZGrEuxykYkv9z8Hl3exx_LKCtjsqEo1OWx8BkLrOQ/viewform?usp=sf_link) to vote for prioritizing adding support for the technologies you are using, or [get involved in the agent development](https://github.com/elastic/apm-agent-java/blob/main/CONTRIBUTING.md) and contribute to the auto-instrumentation capabilities of the agent.


## The Elastic APM Java Agent is not using the latest log4j2 version. Is it still safe? [faq-log4j2-security]

Yes, the log4j version used contains backports for all known security vulnerabilities, including log4shell. More info on [log4j2’s security page](https://logging.apache.org/log4j/2.x/security.md). As the Elastic APM Java Agent still supports Java 7, we can’t update beyond log4j 2.12.x. Some security tools may still falsely flag the log4j2 version that the Elastic APM Java Agent uses as vulnerable. For these cases we publish a dedicated build which ships the latest log4j2 dependency, which however therefore requires at least Java 8. You can find this version on Maven Central linked at our [setup documentation](/reference/setup-javaagent.md#setup-javaagent-get-agent). If there’s a new vulnerability that’s not yet patched in the latest version of the Elastic APM Java Agent, please report it as described in [https://www.elastic.co/community/security](https://www.elastic.co/community/security).

