package com.example.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class MetricLoggerAspect {

    private static final String TAG_METHOD_NAME = "method.name";
    private static final String TAG_METHOD_ACTION = "method.action";
    private static final String TAG_METHOD_OUTCOME = "method.outcome";

    private static final String METHODSTATS_COUNTER_NAME = "methodstats_count";
    private static final String METHODSTATS_GAUGE_NAME = "methodstats_gauge";
    private static final String METHODSTATS_TIMER_NAME = "methodstats_timer";

    private static Logger logger = LoggerFactory.getLogger(MetricLoggerAspect.class);

    private MeterRegistry meterRegistry;

    @Autowired
    public MetricLoggerAspect(MeterRegistry meterRegistry, ApplicationContext applicationContext) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* *(..)) && @annotation(methodStats)")
    public Object log(ProceedingJoinPoint point, MethodStats methodStats) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = point.proceed();
        try {
            captureResponseTime(methodStats, point, System.currentTimeMillis() - start);
            incrementCount(methodStats, point, false);
        } catch (Exception ex) {
            logger.error("Error while capturing metrics.", ex);
        }
        return result;
    }

    @AfterThrowing(
            pointcut = "execution(* *(..)) && @annotation(methodStats)",
            throwing = "excep")
    public void afterThrowing(JoinPoint joinPoint, MethodStats methodStats, Throwable excep) {
        try {
            incrementCount(methodStats, joinPoint, true);
        } catch (Exception ex) {
            logger.error("Error while capturing metrics.", ex);
        }
    }

    private void incrementCount(MethodStats methodStats, JoinPoint joinPoint, boolean errored) {
        if (methodStats.captureCount()) {
            Map<String, String> tags = getTags(methodStats, joinPoint, errored);

            Metrics.NameBuilder metricName = Metrics.name(METHODSTATS_COUNTER_NAME)
                    .withTags(tags);

            meterRegistry.counter(metricName.build()).increment();
        }
    }

    private void captureResponseTime(MethodStats methodStats, JoinPoint joinPoint, long timeTaken) {
        if (methodStats.captureResponseTime()) {
            Map<String, String> tags = getTags(methodStats, joinPoint, null);

            Metrics.NameBuilder metricNameGauge = Metrics.name(METHODSTATS_GAUGE_NAME)
                    .withTags(tags);

            Gauge.builder(metricNameGauge.build(), this, value -> timeTaken)
                    .strongReference(true)//Add strong reference
                    .register(meterRegistry);

            Metrics.NameBuilder metricNameTimer = Metrics.name(METHODSTATS_TIMER_NAME)
                    .withTags(tags);

            meterRegistry.timer(metricNameTimer.build()).record(timeTaken, TimeUnit.MILLISECONDS);
        }
    }

    private Map<String, String> getTags(MethodStats methodStats, JoinPoint joinPoint, Boolean error) {

        String methodName = getMethodName(methodStats, joinPoint);

        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_METHOD_NAME, methodName);

        if (error != null) {
            if (error)
                tags.put(TAG_METHOD_OUTCOME, MethodOutcome.ERROR.toString());
            else
                tags.put(TAG_METHOD_OUTCOME, MethodOutcome.SUCCESS.toString());
        }

        if (methodStats.methodAction() != MethodAction.NONE)
            tags.put(TAG_METHOD_ACTION, methodStats.methodAction().toString());

        addAdditionalTags(methodStats, tags);
        addParamsMarkedForTagging(joinPoint, tags);
        return tags;
    }

    private void addAdditionalTags(MethodStats methodStats, Map<String, String> tags) {
        if (!StringUtils.isEmpty(methodStats.additionalTags())) {
            String[] keyValues = methodStats.additionalTags().split(",");

            if (keyValues.length == 0) {
                return;
            }
            if (keyValues.length % 2 == 1) {
                logger.error("size must be even, it is a set of key=value pairs.");
            }
            for (int i = 0; i < keyValues.length; i += 2) {
                tags.put(keyValues[i], keyValues[i + 1]);
            }
        }
    }

    private String getMethodName(MethodStats methodStats, JoinPoint joinPoint) {
        String methodName;
        if (StringUtils.isEmpty(methodStats.methodName()))
            methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        else
            methodName = methodStats.methodName();

        methodName = methodName.replace(".", "_");
        return methodName.toLowerCase();
    }

    private void addParamsMarkedForTagging(JoinPoint joinPoint, Map<String, String> tags) {
        Object[] methodArgs = joinPoint.getArgs();

        // check which parameter has been marked for tagging
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        int i = 0;
        while (i < parameters.length) {
            final Parameter param = parameters[i];
            if (param.getAnnotation(AddAsTag.class) != null &&
                    !StringUtils.isEmpty(param.getAnnotation(AddAsTag.class).tagName())) {
                tags.put(param.getAnnotation(AddAsTag.class).tagName(), String.valueOf(methodArgs[i]));
            }
            i++;
        }
    }
}
