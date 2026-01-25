package net.neoforged.camelot.module.logging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.BotMain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * The logging handler that logs events related to logging.
 */
public class RoleLogging extends ChannelLogging implements EventListener {
    public RoleLogging(LoggingModule module) {
        super(module, LoggingModule.Type.ROLES);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (gevent instanceof GuildAuditLogEntryCreateEvent event) {
            var entry = event.getEntry();
            if (entry.getType() == ActionType.MEMBER_ROLE_UPDATE) {
                onRolesUpdated(entry);
            }
        }
    }

    private void onRolesUpdated(AuditLogEntry entry) {
        entry.getGuild().retrieveMemberById(entry.getTargetId())
                .queue(targetMember -> {
                    final List<Role> addedRoles = parseRoles(entry, AuditLogKey.MEMBER_ROLES_ADD);
                    if (!addedRoles.isEmpty()) {
                        processEvent(entry, targetMember, "Added", addedRoles, List::removeAll);
                    }

                    final List<Role> removedRoles = parseRoles(entry, AuditLogKey.MEMBER_ROLES_REMOVE);
                    if (!removedRoles.isEmpty()) {
                        processEvent(entry, targetMember, "Removed", removedRoles, List::addAll);
                    }
                }, new ErrorHandler()
                        .ignore(ErrorResponse.UNKNOWN_MEMBER, ErrorResponse.UNKNOWN_USER));
    }

    private void processEvent(AuditLogEntry entry, Member member, String changeType, List<Role> modifiedRoles,
                              BiConsumer<List<Role>, List<Role>> roleListsConsumer) {
        // The role list consumer allows the caller to update the previous roles list with the modified roles list,
        // to guard against the case where the target's roles list has been updated before we receive the event
        // We could also check JDA's impl to see if the event always fires before the target's roles list is updated and
        // get rid of this, but :shrug:
        final List<Role> previousRoles = new ArrayList<>(member.getRoles());
        roleListsConsumer.accept(previousRoles, modifiedRoles);

        final JDA jda = entry.getJDA();
        final EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("User Role(s) " + changeType)
                .setColor(Color.YELLOW)
                .addField("User", member.getUser().getName() + " (" + member.getAsMention() + ")", true)
                .setFooter("User ID: " + member.getUser().getId(), member.getEffectiveAvatarUrl())
                .setTimestamp(entry.getTimeCreated());

        jda.retrieveUserById(entry.getUserIdLong())
                .onErrorMap(ErrorResponse.UNKNOWN_USER::test, _ -> {
                    BotMain.LOGGER.warn("Could not retrieve editor user with ID {} for log entry {}", entry.getUserId(), entry);
                    return null;
                })
                .queue(editor -> {
                    if (editor != null) {
                        embed.setAuthor(editor.getName(), null, editor.getEffectiveAvatarUrl());
                    }

                    embed.addField("Previous Role(s)", LoggingModule.mentionsOrEmpty(previousRoles), true)
                            .addField(changeType + " Role(s)", LoggingModule.mentionsOrEmpty(modifiedRoles), true);

                    log(entry.getGuild(), embed);
                });
    }

    private static List<Role> parseRoles(final AuditLogEntry entry, AuditLogKey logKey) {
        final @Nullable AuditLogChange change = entry.getChangeByKey(logKey);

        if (change == null) return List.of();

        // https://discord.com/developers/docs/resources/audit-log#audit-log-change-object-audit-log-change-exceptions
        // The added/removed roles are marked in the new_value property of the audit log change

        final List<Map<String, String>> roleDatas = Objects.requireNonNull(change.getNewValue());
        final List<Role> roles = new ArrayList<>(roleDatas.size());

        for (final Map<String, String> roleData : roleDatas) {
            final @Nullable Role roleById = entry.getGuild().getRoleById(roleData.get("id"));
            if (roleById == null) {
                BotMain.LOGGER.warn("Could not find role with ID {} while parsing change key {} for log entry {}", roleData, logKey, entry);
                continue;
            }
            roles.add(roleById);
        }

        return roles;
    }
}
