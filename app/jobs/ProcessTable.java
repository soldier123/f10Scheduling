package jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标识处理的表
 * User: wenzhihong
 * Date: 12-10-16
 * Time: 上午9:34
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProcessTable {
    /**
     * 用于标识可以处理的表
     */
    String[] value() default {""};
}
