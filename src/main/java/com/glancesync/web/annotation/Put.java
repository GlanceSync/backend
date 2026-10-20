package com.glancesync.web.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Put { String value() default ""; String[] consumes() default {}; }
