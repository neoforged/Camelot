package net.neoforged.camelot.module.api;

import com.google.common.collect.MapMaker;

import java.util.concurrent.ConcurrentMap;

/**
 * The type of a parameter passed to a module.
 *
 * @param <T> the type of the parameter
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ParameterType<T> {
    private static final ConcurrentMap<Parameters, ParameterType<?>> INTERNER = new MapMaker()
            .weakKeys()
            .makeMap();

    private final String name;
    private final Class<T> type;
    private final Class<? extends CamelotModule<?>> source;

    private ParameterType(String name, Class<T> type, Class<? extends CamelotModule<?>> source) {
        this.name = name;
        this.type = type;
        this.source = source;
    }

    /**
     * {@return the name of the parameter}
     */
    public String getName() {
        return name;
    }

    /**
     * {@return the module source of the parameter}
     */
    public Class<? extends CamelotModule<?>> getSource() {
        return source;
    }

    /**
     * {@return the type of the parameter}
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * {@return an interned parameter}
     */
    public static <T> ParameterType<T> get(String name, Class<T> type, Class<? extends CamelotModule<?>> source) {
        return (ParameterType<T>) INTERNER.computeIfAbsent(new Parameters(name, type, source), _ -> new ParameterType<>(name, type, source));
    }

    /**
     * {@return an interned parameter}
     * <b>Note:</b> must be called from within a module class
     */
    public static <T> ParameterType<T> get(String name, Class<T> type) {
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .getCallerClass();
        if (!CamelotModule.class.isAssignableFrom(caller)) {
            throw new IllegalCallerException("ParameterType#get not called from a module");
        }
        return (ParameterType<T>) INTERNER.computeIfAbsent(new Parameters(name, type, caller), _ -> new ParameterType<>(name, type, (Class)caller));
    }

    private record Parameters(String name, Class<?> type, Class<?> source) {}
}
