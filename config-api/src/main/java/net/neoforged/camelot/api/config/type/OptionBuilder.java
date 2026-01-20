package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.neoforged.camelot.api.config.ConfigOption;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Base interface used to create {@link ConfigOption ConfigOptions}.
 *
 * @param <G> the type of objects that config values are attached to
 * @param <T> the type of the config values
 * @param <S> a recursive reference to the builder type, for chaining purposes
 * @see Options
 * @see OptionRegistrar#option(String, OptionBuilderFactory)
 */
public interface OptionBuilder<G, T, S extends OptionBuilder<G, T, S>> {
    /**
     * Change the display name of the configuration option.
     * By default, the display name is the ID (within the group) of the configuration option.
     * <p>
     * The display name will be shown to users in the menu used to update configuration values.
     *
     * @param name the new display name
     * @return the builder, for chaining purposes
     */
    S displayName(String name);

    /**
     * Change the description of the configuration option.
     * <p>
     * While only the first line of the description will be shown
     * in the top-level configuration update menu, the full description will be shown
     * when the user attempts to change the value (by clicking it in the menu).
     *
     * @param description the new description. Each element in the vararg is a line
     * @return the builder, for chaining purposes
     */
    S description(String... description);

    /**
     * Change the default value of the configuration option.
     * <p>
     * The default value will be returned by {@linkplain ConfigOption#get(Object)} when the target
     * does not have the configuration option explicitly modified.
     * <p>
     * The default default value might be {@code null} or another value, depending on the specific
     * option type (e.g. {@code false} for {@linkplain Options#bool() the boolean option}). Some option types
     * might not allow you to set the default value to {@code null}.
     *
     * @param defaultValue the new default value of this option
     * @return the builder, for chaining purposes
     */
    S defaultValue(T defaultValue);

    /**
     * Create an option builder that wraps this option into a list.
     * <p>
     * The path, id, display name and description of this option builder will be copied over into the list one.
     *
     * @return the builder for this option as a list
     */
    List<G, T> list();

    /**
     * Create an option builder that maps the value produced by this option
     * into another value.
     * <p>
     * The path, id, display name, description and default value of this option builder will be copied over into the mapped one.
     *
     * @param from a function used to convert from this builder's type to the target type
     * @param to   a function used to convert from the target type to this builder's type
     * @param <TO> the type of the object to map to
     * @return the builder for the mapped option
     */
    default <TO> OptionBuilder<G, TO, ?> map(Function<T, TO> from, Function<TO, T> to) {
        return map(from, to, null);
    }

    /**
     * Create an option builder that maps the value produced by this option
     * into another value.
     * <p>
     * The path, id, display name, description and default value of this option builder will be copied over into the mapped one.
     *
     * @param from      a function used to convert from this builder's type to the target type
     * @param to        a function used to convert from the target type to this builder's type
     * @param formatter a function used to convert the value to a human readable form that will be displayed in the configuration menu. If
     *                  {@code null}, this option type's formatter will be used
     * @param <TO>      the type of the object to map to
     * @return the builder for the mapped option
     */
    <TO> OptionBuilder<G, TO, ?> map(Function<T, TO> from, Function<TO, T> to, @Nullable Function<TO, String> formatter);

    /**
     * Create the configuration option according to this builder's parameters and register it.
     *
     * @return the registered config option
     */
    ConfigOption<G, T> register();

    /**
     * A {@link OptionBuilder} for values that can be validated.
     */
    interface Validatable<G, T, S extends Validatable<G, T, S>> extends OptionBuilder<G, T, S> {
        /**
         * Add a validator that will be used to check whether user inputs are valid for this option.
         * <p>
         * If the given {@code validator} returns {@code false}, the input will be rejected and the
         * {@code errorMessage} will be displayed to the user.
         * <p>
         * <b>Note:</b> this method is additive.
         *
         * @param validator    the validator that checks user inputs
         * @param errorMessage the message to display if the validator rejects the input
         * @return the builder, for chaining purposes
         */
        default S validate(Predicate<T> validator, String errorMessage) {
            return validate(value -> validator.test(value) ? null : errorMessage);
        }

        /**
         * Add a validator that will be used to check whether user inputs are valid for this option.
         * <p>
         * Return {@code null} in the validator function for a valid input. Otherwise, the returned
         * string represents the error message.
         * <p>
         * <b>Note:</b> this method is additive.
         *
         * @param validator the validator that checks user inputs
         * @return the builder, for chaining purposes
         */
        S validate(Validator<T> validator);
    }

    /**
     * A {@link OptionBuilder} for values that can be validated and are {@linkplain Comparable comparable}.
     */
    interface Comparable<G, T extends java.lang.Comparable<T>, S extends Comparable<G, T, S>> extends Validatable<G, T, S> {
        /**
         * Adds a {@linkplain #validate(Validator) validator} that checks if the input
         * is greater than or equal to the {@code minValue}.
         *
         * @param minValue the minimum value of user input
         * @return the builder, for chaining purposes
         */
        default S min(T minValue) {
            return validate(i -> i.compareTo(minValue) >= 0, "Input must be greater than or equal to " + minValue);
        }

        /**
         * Adds a {@linkplain #validate(Validator) validator} that checks if the input
         * is smaller than or equal to the {@code maxValue}.
         *
         * @param maxValue the maximum value of user input
         * @return the builder, for chaining purposes
         */
        default S max(T maxValue) {
            return validate(i -> i.compareTo(maxValue) <= 0, "Input must be smaller than or equal to " + maxValue);
        }
    }

    /**
     * A {@link OptionBuilder} for numeral values (ints, etc.).
     */
    interface Number<G, T extends java.lang.Number & java.lang.Comparable<T>> extends Comparable<G, T, Number<G, T>> {
        /**
         * Adds a {@linkplain #validate(Validator) validator} that ensures that
         * user input is positive (that is, greater or equal to zero).
         *
         * @return the builder, for chaining purposes
         */
        Number<G, T> positive();
    }

    /**
     * A {@link OptionBuilder} for text (string).
     */
    interface Text<G> extends Validatable<G, String, Text<G>> {
        /**
         * Restrict the minimum length of the value of this option.
         *
         * @param minLength the minimum length of this option's values
         * @return the builder, for chaining purposes
         */
        Text<G> minLength(int minLength);

        /**
         * Restrict the maximum length of the value of this option.
         *
         * @param maxLength the maximum length of this option's values
         * @return the builder, for chaining purposes
         */
        Text<G> maxLength(int maxLength);

        /**
         * When configured as multiline, this option will prompt a {@link TextInputStyle#PARAGRAPH multiline}
         * text input box in the configuration modal.
         * <p>
         * <b>Note</b>: by default, text options are configured as {@linkplain TextInputStyle#SHORT single-line}.
         *
         * @return the builder, for chaining purposes
         */
        Text<G> multiline();
    }

    /**
     * A {@link OptionBuilder} for collections, adding a few common methods.
     */
    interface Collection<G, T, C extends java.util.Collection<T>, S extends Collection<G, T, C, S>> extends OptionBuilder<G, C, S> {
        /**
         * Restrict the maximum amount of elements values of this option can contain.
         * <p>
         * For instance, if this is set to {@code 25}, users may not add more than 25 elements to the collection,
         * using the configuration menu.
         *
         * @param maxElements the maximum amount of elements values of this option can contain
         * @return the builder, for chaining purposes
         */
        S maxElements(int maxElements);

        /**
         * Restrict the minimum amount of elements values of this option can contain.
         * <p>
         * For instance, if this is set to {@code 4}, users may not remove elements in such a way that
         * less than 4 elements would remain.
         * <p>
         * <b>Note</b>: for this to be used effectively, you have to {@link #defaultValue(Object) set the default value}
         * in such a way that at least the minimum amount of elements are present by default.
         *
         * @param minElements the maximum amount of elements values of this option can contain
         * @return the builder, for chaining purposes
         */
        S minElements(int minElements);

        /**
         * Restrict the amount of elements this option can have to just one, and
         * return an option that either returns that sole element or {@code null} if the collection is empty.
         *
         * @return the builder, for chaining purposes
         */
        OptionBuilder<G, T, ?> justOne();
    }

    /**
     * A {@link OptionBuilder} for lists.
     *
     * @see OptionBuilder#list()
     */
    interface List<G, T> extends Collection<G, T, java.util.List<T>, List<G, T>> {
        @Override
        default OptionBuilder<G, T, ?> justOne() {
            return maxElements(1).map(
                    l -> l.isEmpty() ? null : l.getFirst(),
                    el -> el == null ? java.util.List.of() : java.util.List.of(el)
            );
        }
    }

    /**
     * A {@link OptionBuilder} for sets.
     */
    interface Set<G, T> extends Collection<G, T, java.util.Set<T>, Set<G, T>> {
        @Override
        default OptionBuilder<G, T, ?> justOne() {
            return maxElements(1).map(
                    l -> l.isEmpty() ? null : l.iterator().next(),
                    el -> el == null ? java.util.Set.of() : java.util.Set.of(el)
            );
        }
    }
}
