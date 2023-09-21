package net.neoforged.camelot.db.impl;

import com.google.common.collect.Multimaps;
import net.neoforged.camelot.db.api.CallbackConfig;
import net.neoforged.camelot.db.api.ExecutionCallback;
import org.jdbi.v3.sqlobject.Handler;
import org.jdbi.v3.sqlobject.HandlerDecorator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class PostCallbackDecorator implements HandlerDecorator {
    private final CallbackConfig config;

    public PostCallbackDecorator(CallbackConfig config) {
        this.config = config;
    }

    @Override
    public Handler decorateHandler(Handler base, Class<?> sqlObjectType, Method method) {
        final var callbackMethods = config.getCallbackClasses(sqlObjectType)
                .flatMap(clz -> Stream.of(clz.getMethods()))
                .filter(callback -> Modifier.isStatic(callback.getModifiers()) && callback.getAnnotation(ExecutionCallback.class) != null)
                .filter(callback -> {
                    final ExecutionCallback annotation = callback.getAnnotation(ExecutionCallback.class);
                    return annotation.methodName().equals(method.getName()) && (annotation.phase() == ExecutionCallback.Phase.POST && method.getReturnType() != void.class ?
                            (Arrays.equals(method.getParameterTypes(), 0, method.getParameterTypes().length, callback.getParameterTypes(), 1, callback.getParameterTypes().length - 1)
                                    && callback.getParameterTypes()[callback.getParameterCount() - 1] == method.getReturnType()
                                    && callback.getParameterTypes()[0] == sqlObjectType) :
                        (callback.getParameterTypes()[0] == sqlObjectType && Arrays.equals(method.getParameterTypes(), 0, method.getParameterTypes().length, callback.getParameterTypes(), 1, callback.getParameterTypes().length)));
                })
                .collect(Multimaps.toMultimap(
                        c -> c.getAnnotation(ExecutionCallback.class).phase(),
                        Function.identity(),
                        () -> Multimaps.newMultimap(new EnumMap<>(ExecutionCallback.Phase.class), ArrayList::new)
                ));
        if (callbackMethods.isEmpty()) return base;

        return (target, args, handle) -> {
            for (final Method callback : callbackMethods.get(ExecutionCallback.Phase.PRE)) {
                final Object[] invocationArgs = new Object[args.length + 1];
                invocationArgs[0] = target;
                System.arraycopy(args, 0, invocationArgs, 1, args.length);
                callback.invoke(null, invocationArgs);
            }
            final Object result = base.invoke(target, args, handle);

            for (final Method callback : callbackMethods.get(ExecutionCallback.Phase.POST)) {
                final Object[] invocationArgs;
                if (method.getReturnType() == void.class) {
                    invocationArgs = new Object[args.length + 1];
                    invocationArgs[0] = target;
                    System.arraycopy(args, 0, invocationArgs, 1, args.length);
                } else {
                    invocationArgs = new Object[args.length + 2];
                    invocationArgs[0] = target;
                    System.arraycopy(args, 0, invocationArgs, 1, args.length);
                    invocationArgs[args.length + 1] = result;
                }

                callback.invoke(null, invocationArgs);
            }
            return result;
        };
    }
}
