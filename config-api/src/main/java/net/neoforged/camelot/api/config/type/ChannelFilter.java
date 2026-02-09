package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Represents a filter for {@link Channel}s.
 *
 * @param allByDefault whether this filter should match all channels by default (if they are neither blacklisted nor whitelisted).
 * @param whitelist    channels (or channel categories) to include
 * @param blacklist    channels (or channel categories) to exclude
 *
 * @see Options#channelFilter()
 */
public record ChannelFilter(boolean allByDefault, Set<Long> whitelist,
                            Set<Long> blacklist, boolean simple) implements Predicate<Channel> {
    public static final ChannelFilter ALL_BY_DEFAULT = new ChannelFilter(true, Set.of(), Set.of());
    public static final ChannelFilter NONE_BY_DEFAULT = new ChannelFilter(false, Set.of(), Set.of());

    public ChannelFilter(boolean allByDefault, Set<Long> whitelist, Set<Long> blacklist) {
        this(allByDefault, whitelist, blacklist, false);
    }

    @ApiStatus.Internal
    public ChannelFilter {
        whitelist = Set.copyOf(whitelist);
        blacklist = Set.copyOf(blacklist);
        simple = whitelist.isEmpty() && blacklist.isEmpty();
    }

    @Override
    public boolean test(Channel channel) {
        if (simple) return allByDefault;

        if (blacklist.contains(channel.getIdLong())) return false;
        if (whitelist.contains(channel.getIdLong())) return true;

        if (channel instanceof ThreadChannel thread) {
            return test(thread.getParentChannel());
        }

        if (channel instanceof ICategorizableChannel categorizable) {
            if (blacklist.contains(categorizable.getParentCategoryIdLong())) return false;
            if (whitelist.contains(categorizable.getParentCategoryIdLong())) return true;
        }

        return allByDefault;
    }

    public String format() {
        if (!allByDefault && whitelist.isEmpty() && blacklist.isEmpty()) {
            return "none of the channels";
        }

        var str = new StringBuilder();
        if (allByDefault) {
            str.append("all channels");
            if (!blacklist.isEmpty()) {
                str.append(" excluding ").append(formatMentions(blacklist, "#"));
                if (!whitelist.isEmpty()) {
                    str.append(", but including ").append(formatMentions(whitelist, "#"));
                }
            }
        } else {
            if (!whitelist.isEmpty()) {
                str.append(formatMentions(whitelist, "#"));
            }
            if (!blacklist.isEmpty()) {
                str.append(" excluding ").append(formatMentions(blacklist, "#"));
            }
        }
        return str.toString();
    }

    @ApiStatus.Internal
    public static String formatMentions(Set<Long> set, String mentionCharacter) {
        if (set.isEmpty()) return "";
        StringBuilder str = new StringBuilder();
        int idx = 0;
        for (Long id : set) {
            if (idx > 0 && idx == set.size() - 1) {
                str.append(" and ");
            } else if (idx > 0) {
                str.append(", ");
            }
            str.append("<").append(mentionCharacter).append(id).append('>');
            idx++;
        }
        return str.toString();
    }
}
