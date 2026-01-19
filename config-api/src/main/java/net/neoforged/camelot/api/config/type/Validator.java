package net.neoforged.camelot.api.config.type;

import org.jetbrains.annotations.Nullable;

/**
 * Functional interface used to validate user inputs.
 *
 * @param <T> the type of the input
 */
@FunctionalInterface
public interface Validator<T> {
    /**
     * Validate the given {@code value}.
     * <p>
     * {@return null if the value is valid. Otherwise, the validation error message to show to the user}
     * (e.g. "Number must be positive")
     *
     * @param value the value to validate
     */
    @Nullable
    String validate(T value);

    /**
     * Chain this validator with the given validator.
     * If this validator validates a value, the supplied validator will then be asked to validate.
     *
     * @param validator the validator to chain
     * @return a new validator combining this validator and the given one
     */
    default Validator<T> or(Validator<T> validator) {
        return value -> {
            var validated = validate(value);
            return validated == null ? validator.validate(value) : validated;
        };
    }
}
