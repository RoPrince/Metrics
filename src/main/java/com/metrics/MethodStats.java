package com.example.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodStats {
    String methodName() default "";

    String additionalTags() default "";

    MethodAction methodAction() default MethodAction.NONE;

    boolean captureCount() default true;

    boolean captureResponseTime() default true;
}
