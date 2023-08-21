package net.neoforged.camelot.db.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a method inside a {@link CallbackConfig#registerCallbackClass(Class, Class) callback class} with this annotation
 * in order to make that method a callback.
 * <p>A callback method must be static, have the transactional instance as the first parameter, followed by the target method parameters
 * and finally, if the target method returns a non-void type, the return type of the target method.</p>
 *
 * @see CallbackConfig#registerCallbackClass(Class, Class)
 * @see RegisterExecutionCallbacks
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExecutionCallback {
    enum Phase {
        /**
         * Runs before the target method's statement is executed.
         */
        PRE,

        /**
         * Runs after the target method's statement is executed, and <strong>only if</strong> the statement was successful.
         */
        POST
    }

    /**
     * {@return the phase of this callback}
     */
    Phase phase() default Phase.POST;

    /**
     * {@return the name of the method this callback is for}
     */
    String methodName();
}
