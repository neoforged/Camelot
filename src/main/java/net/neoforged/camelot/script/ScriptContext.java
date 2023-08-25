package net.neoforged.camelot.script;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.transactionals.CountersDAO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The context of a script execution.
 *
 * @param reply a consumer used to send a message to the user
 */
public record ScriptContext(
        JDA jda, Guild guild, Member member, MessageChannel channel, ScriptReplier reply
) {
    private static final Map<Class<?>, ScriptTransformer<?>> TRANSFORMERS = new IdentityHashMap<>();

    /**
     * Transforms the given object to one which may be given to a script execution.
     *
     * @param context     the context to transform the script with
     * @param toTransform the object to transform
     * @return the transformed object
     */
    public static Object transform(ScriptContext context, Object toTransform) {
        return getTransformer(toTransform).transform(context, toTransform);
    }

    /**
     * Gets the {@link ScriptTransformer} for the given {@code obj}, usually based on its {@link Object#getClass() class}.
     *
     * @param obj the object for which to get a transformer
     * @param <T> the type of the object
     * @return the transformer
     */
    @SuppressWarnings("unchecked")
    public static <T> ScriptTransformer<T> getTransformer(T obj) {
        return (ScriptTransformer<T>) TRANSFORMERS.computeIfAbsent(obj.getClass(), k -> {
            if (obj instanceof User) {
                return cast(ScriptContext::createUser);
            } else if (obj instanceof Member) {
                return cast(ScriptContext::createMember);
            } else if (obj instanceof Channel) {
                return cast(ScriptContext::createChannel);
            } else if (obj instanceof Role) {
                return cast(ScriptContext::createRole);
            } else if (obj instanceof Guild) {
                return cast(ScriptContext::createGuild);
            } else if (obj instanceof JDA) {
                return cast(ScriptContext::createJDA);
            } else if (obj instanceof List<?>) {
                return cast(ScriptContext::transformList);
            }

            return (context, object) -> object;
        });
    }

    /**
     * Compiles this context into a {@link ScriptObject} containing the full context.
     *
     * @return the script object
     */
    public ScriptObject compile() {
        return ScriptObject.of("Script")
                // The context with which the script was executed
                .put("guild", createGuild(guild))
                .put("member", createMember(member))
                .put("channel", createChannel(channel))
                .put("user", createUser(member.getUser()))
                .put("jda", createJDA(jda))

                // Methods used for replying
                .putVoidMethod("reply", args -> reply.accept(MessageCreateData.fromContent(args.argString(0, true))))
                .put("console", ScriptObject.of("console")
                        .putVoidMethod("log", args -> reply.accept(MessageCreateData.fromContent(Arrays.stream(args.getArguments())
                                .map(ScriptUtils::toString).collect(Collectors.joining())))))
                .putVoidMethod("replyEmbed", args -> reply.accept(MessageCreateData.fromEmbeds(args.argMap(0, true).asEmbed())));
    }

    public List<Object> transformList(List<?> other) {
        return other.stream().map(o -> transform(this, o)).toList();
    }

    public ScriptObject createUser(User user) {
        return ScriptObject.mentionable("User", user)
                .put("name", user.getName())
                .put("discriminator", user.getDiscriminator())
                .put("avatarUrl", user.getAvatarUrl())
                .putMethod("asTag", args -> user.getAsTag())
                .putMethod("toString", args -> user.getAsTag());
    }

    public ScriptObject createMember(Member member) {
        return ScriptObject.mentionable("Member", member)
                .put("user", createUser(member.getUser()))
                .put("avatarUrl", member.getAvatarUrl())
                .put("effectiveAvatarUrl", member.getEffectiveAvatarUrl())
                .put("nickname", member.getNickname())
                .put("effectiveName", member.getEffectiveName())
                .putLazyGetter("getPermissions", () -> new ArrayList<>(member.getPermissions()))
                .putLazyGetter("getRoles", () -> transformList(member.getRoles()))
                .putMethod("toString", args -> member.getUser().getAsTag() + " in " + member.getGuild().getName());
    }

    public ScriptObject createChannel(Channel channel) {
        return ScriptObject.mentionable("Channel", channel)
                .put("name", channel.getName())
                .put("type", channel.getType());
    }

    public ScriptObject createGuild(Guild guild) {
        return ScriptObject.snowflake("Guild", guild)
                .put("name", guild.getName())
                .putMethod("getRoles", args -> transformList(guild.getRoles()))
                .putMethod("getCounter", args -> Database.main().withExtension(CountersDAO.class,
                        db -> db.getCounterAmount(guild.getIdLong(), args.argString(0, true))));
    }

    public ScriptObject createRole(Role role) {
        return ScriptObject.mentionable("Role", role)
                .put("name", role.getName())
                .putLazyGetter("getGuild", () -> createGuild(role.getGuild()));
    }

    public ScriptObject createJDA(JDA jda) {
        return ScriptObject.of("JDA")
                .putMethod("getUserById", args -> createUser(jda.retrieveUserById(args.argString(0, true)).complete()));
    }

    /**
     * An interface used to transform Java objects into objects which may be fed into script execution {@link org.graalvm.polyglot.Context contexts}.
     *
     * @param <T> the type of the object this transformer transforms
     */
    @FunctionalInterface
    public interface ScriptTransformer<T> {
        Object transform(ScriptContext context, T object);
    }

    @SuppressWarnings("unchecked")
    private static <F, T> ScriptTransformer<T> cast(ScriptTransformer<F> transformer) {
        return (ScriptTransformer<T>) transformer;
    }
}
