package jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识性注解, 用于任务的描述
 * User: wenzhihong
 * Date: 12-10-24
 * Time: 上午11:59
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JobDesc {
    /**
     * 描述信息
     * @return
     */
    String desc() default "";
}
