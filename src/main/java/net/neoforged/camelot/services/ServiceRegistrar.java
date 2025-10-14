package net.neoforged.camelot.services;

import net.neoforged.camelot.Bot;

/**
 * Registrar used to register {@link CamelotService}s to the {@link Bot}.
 *
 * @see CamelotService
 */
public interface ServiceRegistrar {
    /**
     * Register the given {@code service} as a service of the given {@code type}.
     *
     * @param type    the type of the service
     * @param service the service to register
     * @param <T>     the type of the service
     */
    <T extends CamelotService> void register(Class<T> type, T service);
}
