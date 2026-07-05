package io.github.gsa;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;
import java.util.*;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class GradleSwitchAgent {
    public static final boolean DEBUG_INSTRUMENTATION = Boolean.getBoolean("gsa.debug.instrumentation");
    public static final boolean DEBUG_RESOLUTION = Boolean.getBoolean("gsa.debug.resolution");

    public static Map<String, String> overrides = new HashMap<>();
    public static Map<String, ValueTransformer> transforms = new HashMap<>();

    public static synchronized void premain(String arguments, Instrumentation instrumentation) {
        Map<String, String> parsed = new HashMap<>();
        for (String pair : arguments.split(",")) {
            String[] kv = pair.split("=", 2);
            String key = kv[0].trim();
            if (kv.length == 1) {
                if (key.isEmpty()) continue;
                throw new IllegalArgumentException("Malformed pair '" + pair + "'. Format is: 'key=value,key=value...'");
            }
            String value = kv[1].trim();

            if (key.endsWith("~")) {
                String tKey = key.substring(0, key.length() - 1);
                transforms.computeIfAbsent(tKey, k -> new ValueTransformer());

                String separator = String.valueOf(value.charAt(0));
                String[] parts = value.split(Pattern.quote(separator), -1);

                String err = null;
                if (parts.length != 4) {
                    err = "Transform pattern format: /regex/replacement/ (any separator char works)";
                }
                if (err == null && !(parts[0].isEmpty() && parts[3].isEmpty())) {
                    err = "No text allowed before/after the leading/trailing separator";
                }
                if (err != null) {
                    System.err.println(err);
                    System.err.println("Got: '" + value + "' -> " + Arrays.toString(parts));
                    System.exit(1);
                }

                transforms.get(tKey).addTransform(Pattern.compile(parts[1]), parts[2]);
            } else {
                parsed.put(key, value);
            }
        }
        overrides.putAll(parsed);

        AgentBuilder.Transformer.ForAdvice adviser = new AgentBuilder.Transformer.ForAdvice();
        AgentBuilder builder = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        if (DEBUG_INSTRUMENTATION) {
            builder = builder.with(AgentBuilder.Listener.StreamWriting.toSystemError());
        }

        builder.type(named("org.gradle.wrapper.WrapperExecutor"))
                .transform(adviser.advice(
                        named("getProperty").and(takesArguments(3)),
                        GradleSwitchAgent.class.getName() + "$OverrideProperty"
                )).installOn(instrumentation);
    }

    public static class ValueTransformer {
        List<Pattern> patterns = new ArrayList<>();
        List<String> replaces = new ArrayList<>();

        void addTransform(Pattern pattern, String replace) {
            patterns.add(pattern);
            replaces.add(replace);
        }

        public String transformValue(String value) {
            String current = value;
            for (int i = 0; i < patterns.size(); i++) {
                current = patterns.get(i).matcher(current).replaceAll(replaces.get(i));
            }
            return current;
        }
    }

    static class OverrideProperty {
        @Advice.OnMethodEnter(skipOn = String.class)
        static String lookupOverride(final String propertyName) {
            return overrides.get(propertyName);
        }

        @Advice.OnMethodExit
        static void applyOverrideOrTransform(
                @Advice.Argument(0) final String propName,
                @Advice.Enter final String override,
                @Advice.Return(readOnly = false) String retval
        ) {
            if (override != null) {
                if (DEBUG_RESOLUTION) {
                    System.err.printf("Overrode Gradle Wrapper property: %s = '%s'%n", propName, override);
                }
                retval = override;
                return;
            }

            ValueTransformer transformer = transforms.get(propName);
            if (transformer != null) {
                String transformed = transformer.transformValue(retval);
                if (DEBUG_RESOLUTION) {
                    System.err.printf("Transformed Gradle Wrapper property: %s = '%s' -> '%s'%n", propName, retval, transformed);
                }
                retval = transformed;
            }
        }
    }
}
