package net.neoforged.camelot.script.option;

import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptOptions;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.util.Optional;
import java.util.regex.Matcher;

/**
 * An {@link org.kohsuke.args4j.spi.OptionHandler} that parses Discord users, roles, guilds or channels.
 */
public class MentionableOptionHandler extends OneArgumentOptionHandler<IMentionable> {
    public final Message.MentionType mentionType;
    private final boolean isMember;
    private final ScriptContext context;

    @SuppressWarnings("rawtypes")
    public MentionableOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super IMentionable> setter) {
        super(parser, option, setter);
        final boolean isMemberType = setter.getType() == (Class) Member.class;
        if (setter.getType() == (Class) User.class || isMemberType) {
            mentionType = Message.MentionType.USER;
            isMember = isMemberType;
        } else if (setter.getType() == (Class) Role.class) {
            mentionType = Message.MentionType.ROLE;
            isMember = false;
        } else if (setter.getType() == (Class) Channel.class) {
            mentionType = Message.MentionType.CHANNEL;
            isMember = false;
        } else {
            throw new IllegalArgumentException("A mentionable option only supports users, members roles and channels!");
        }
        if (!(setter instanceof ScriptOptions.ListSetter<?> st)) {
            throw new IllegalArgumentException("Can only use mentionable options with script setters!");
        }
        this.context = st.context;
    }

    @Override
    protected IMentionable parse(String argument) throws NumberFormatException, CmdLineException {
        long id;
        try {
            id = Long.parseUnsignedLong(argument);
        } catch (NumberFormatException ignored) {
            final Matcher matcher = mentionType.getPattern().matcher(argument);
            if (!matcher.find()) {
                throw new CmdLineException(owner, new NoLocaleLocalizable("Invalid mention \"" + argument + "\"; not of type " + mentionType));
            } else {
                id = Long.parseUnsignedLong(matcher.group(1));
            }
        }

        final long finalId = id;
        return switch (mentionType) {
            case CHANNEL -> Optional.ofNullable(context.jda().getChannelById(Channel.class, finalId))
                    .orElseThrow(() -> new CmdLineException(owner, "Unknown channel with id " + finalId, null));
            case ROLE -> Optional.ofNullable(context.guild().getRoleById(id))
                    .orElseThrow(() -> new CmdLineException(owner, "Unknown role with id " + finalId, null));
            case USER -> {
                if (isMember) {
                    yield context.guild().retrieveMemberById(id).complete();
                } else {
                    yield context.jda().retrieveUserById(id).complete();
                }
            }
            default -> throw new IllegalArgumentException(); // How did we get here?
        };
    }

    @Override
    public String getDefaultMetaVariable() {
        return getMeta(mentionType, isMember);
    }

    public static String getMeta(Message.MentionType mentionType, boolean isMember) {
        return switch (mentionType) {
            case ROLE -> "@role";
            case USER -> isMember ? "@member" : "@user";
            case CHANNEL -> "#channel";
            default -> throw new IllegalArgumentException();
        };
    }
}
