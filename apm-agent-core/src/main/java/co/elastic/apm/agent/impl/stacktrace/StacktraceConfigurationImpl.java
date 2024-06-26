/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.impl.stacktrace;

import co.elastic.apm.agent.tracer.configuration.StacktraceConfiguration;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import co.elastic.apm.agent.tracer.configuration.TimeDurationValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class StacktraceConfigurationImpl extends ConfigurationOptionProvider implements StacktraceConfiguration {

    private static final String STACKTRACE_CATEGORY = "Stacktrace";
    public static final String APPLICATION_PACKAGES = "application_packages";
    private final ConfigurationOption<Collection<String>> applicationPackages = ConfigurationOption.stringsOption()
        .key(APPLICATION_PACKAGES)
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.\n" +
            "This allows the APM app to collapse the stack frames of library code,\n" +
            "and highlight the stack frames that originate from your application.\n" +
            "Multiple root packages can be set as a comma-separated list;\n" +
            "there's no need to configure sub-packages.\n" +
            "Because this setting helps determine which classes to scan on startup,\n" +
            "setting this option can also improve startup time.\n" +
            "\n" +
            "You must set this option in order to use the API annotations `@CaptureTransaction` and `@CaptureSpan`.\n" +
            "\n" +
            "**Example**\n" +
            "\n" +
            "Most Java projects have a root package, e.g. `com.myproject`. " +
            "You can set the application package using Java system properties:\n" +
            "`-Delastic.apm.application_packages=com.myproject`\n" +
            "\n" +
            "If you are only interested in specific subpackages, you can separate them with commas:\n" +
            "`-Delastic.apm.application_packages=com.myproject.api,com.myproject.impl`\n\n" +
            "NOTE: the instrumentation aspect of this configuration option - specifying which classes to scan" +
            " - only applies at startup of the agent and changing the value later won't affect which classes" +
            "got scanned. The UI aspect, showing where stack frames can be collapsed, can be changed at any time.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Integer> stackTraceLimit = ConfigurationOption.integerOption()
        .key("stack_trace_limit")
        .tags("performance")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("Setting it to 0 will disable stack trace collection. " +
            "Any positive integer value will be used as the maximum number of frames to collect. " +
            "Setting it -1 means that all frames will be collected.")
        .dynamic(true)
        .buildWithDefault(50);

    private final ConfigurationOption<TimeDuration> spanFramesMinDurationMs = TimeDurationValueConverter.durationOption("ms")
        .key("span_frames_min_duration")
        .aliasKeys("span_frames_min_duration_ms")
        .tags("internal", "deprecated")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("While this is very helpful to find the exact place in your code that causes the span, " +
            "collecting this stack trace does have some overhead. " +
            "\n" +
            "When setting this option to a negative value, like `-1ms`, stack traces will be collected for all spans. " +
            "Setting it to a positive value, e.g. `5ms`, will limit stack trace collection to spans " +
            "with durations equal to or longer than the given value, e.g. 5 milliseconds.\n" +
            "\n" +
            "To disable stack trace collection for spans completely, set the value to `0ms`.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("5ms"));

    private final ConfigurationOption<TimeDuration> spanStackTraceMinDurationMs = TimeDurationValueConverter.durationOption("ms")
        .key("span_stack_trace_min_duration")
        .tags("performance")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("While this is very helpful to find the exact place in your code that causes the span, " +
            "collecting this stack trace does have some overhead. " +
            "\n" +
            "When setting this option to value `0ms`, stack traces will be collected for all spans. " +
            "Setting it to a positive value, e.g. `5ms`, will limit stack trace collection to spans " +
            "with durations equal to or longer than the given value, e.g. 5 milliseconds.\n" +
            "\n" +
            "To disable stack trace collection for spans completely, set the value to `-1ms`.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("5ms"));

    @Override
    public Collection<String> getApplicationPackages() {
        return applicationPackages.get();
    }

    public int getStackTraceLimit() {
        return stackTraceLimit.get();
    }

    public long getSpanStackTraceMinDurationMs() {
        if (spanStackTraceMinDurationMs.isDefault() && !spanFramesMinDurationMs.isDefault()) {
            long spanFramesMinDurationMsValue = spanFramesMinDurationMs.getValue().getMillis();
            if (spanFramesMinDurationMsValue == 0) {
                return -1;
            } else if (spanFramesMinDurationMsValue < 0) {
                return 0;
            } else {
                return spanFramesMinDurationMsValue;
            }
        }
        return spanStackTraceMinDurationMs.getValue().getMillis();
    }
}
