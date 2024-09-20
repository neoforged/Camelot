package net.neoforged.camelot.module.stickyroles;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record StickyRolesListener(Jdbi database) implements EventListener {
    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        switch (gevent) {
            case GuildMemberJoinEvent joinEvent -> onJoin(joinEvent);
            case GuildMemberRemoveEvent event -> onLeave(event);
            default -> {}
        }
    }

    private void onLeave(GuildMemberRemoveEvent event) {
        database.useExtension(StickyRolesDAO.class, db -> {
            var config = db.getConfiguration(event.getGuild().getIdLong());
            if (config == null) return;

            db.clear(event.getUser().getIdLong(), event.getGuild().getIdLong());
            final var roles = config.rolesToStick(event.getMember().getRoles().stream()
                    .filter(r -> !r.isManaged() && event.getGuild().getSelfMember().canInteract(r)));
            db.insert(event.getUser().getIdLong(), event.getGuild().getIdLong(), roles.iterator());
        });
    }

    private void onJoin(GuildMemberJoinEvent event) {
        database.useExtension(StickyRolesDAO.class, db -> {
            var config = db.getConfiguration(event.getGuild().getIdLong());
            if (config == null) return;

            var roles = db.getRoles(event.getUser().getIdLong(), event.getGuild().getIdLong());
            if (roles.isEmpty()) return;

            var rolesToAdd = config.rolesToStick(roles.stream().mapToLong(l -> l))
                    .mapToObj(event.getGuild()::getRoleById)
                    .filter(Objects::nonNull)
                    .toList();

            if (!rolesToAdd.isEmpty()) {
                event.getGuild()
                        .modifyMemberRoles(event.getMember(), rolesToAdd, null)
                        .reason("Persisted roles")
                        .queue();
            }

            db.clear(event.getUser().getIdLong(), event.getGuild().getIdLong());
        });
    }
}
