package net.neoforged.camelot.db.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a transactional class with this method to register execution callback classes as an alternative to {@link CallbackConfig#registerCallbackClass(Class, Class)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterExecutionCallbacks {
    /**
     * {@return the callback classes to register}
     */
    Class<?>[] value();
}
