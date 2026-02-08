package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.neoforged.camelot.api.config.type.HumanReadableEnum;
import net.neoforged.camelot.api.config.type.ObjectOptionBuilders;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionBuilderFactory;
import net.neoforged.camelot.api.config.type.entity.EntitySet;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@SuppressWarnings({"unchecked", "rawtypes"})
public class OptionsImpl {
    public static final OptionBuilderFactory BOOL = BooleanOption.Builder::new;
    public static final OptionBuilderFactory STRING = StringOption::builder;
    public static final OptionBuilderFactory REGEX = (manager, path, id) -> ((StringOption.Builder<?>) StringOption.builder(manager, path, id))
            .validate((String in) -> {
                try {
                    Pattern.compile(in);
                    return null;
                } catch (PatternSyntaxException ex) {
                    return ex.getMessage();
                }
            })
            .map(Pattern::compile, Pattern::pattern, in -> "`" + in.pattern() + "`");
    public static final OptionBuilderFactory INT = (manager, path, id) -> new NumberOption.Builder<>(manager, path, id, Integer::parseInt, Number::intValue);

    public static final OptionBuilderFactory DURATION = DurationOption.Builder::new;
    public static final OptionBuilderFactory CHANNEL_FILTER = ChannelFilterOption.Builder::new;

    public static final OptionBuilderFactory
            ROLES = (manager, path, id) -> new EntityOption.Builder<>(manager, path, id, EntitySelectMenu.SelectTarget.ROLE, EntityOption.RoleSet::new),
            CHANNELS = (manager, path, id) -> new EntityOption.Builder<>(manager, path, id, EntitySelectMenu.SelectTarget.CHANNEL, EntityOption.ChannelSet::new);

    public static <G, E extends Enum<E> & HumanReadableEnum> OptionBuilderFactory<G, Set<E>, OptionBuilder.Set<G, E>> enumeration(Class<E> type) {
        return (manager, path, id) -> new EnumOption.Builder<>(manager, path, id, type, HumanReadableEnum::humanReadableName, HumanReadableEnum::description);
    }

    public static <G> OptionBuilderFactory<G, EntitySet, EntitySet.Builder<G, EntitySet>> entities(EntitySelectMenu.SelectTarget entityType) {
        return (manager, path, id) -> new EntityOption.Builder<>(manager, path, id, entityType, EntityOption.BaseEntitySet::new);
    }

    public static <G, T> OptionBuilder.CompositeStarter<G, T> composite(Class<T> type) {
        return new OptionBuilder.CompositeStarter<>() {
            @Override
            public <F, FR, B extends OptionBuilder<G, FR, B>> ObjectOptionBuilders.Builder1<G, T, F> field(String id, Function<T, F> extractor, OptionBuilderFactory<G, FR, B> fieldFactory, Function<B, OptionBuilder<G, F, ?>> optionConfigurator) {
                return new ObjectOptionBuilder<>(List.of(new ObjectOption.BuilderOption(id, extractor, fieldFactory, optionConfigurator)));
            }
        };
    }
}
