package net.neoforged.camelot.module;

import com.google.common.primitives.Ints;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import j2html.tags.DomContent;
import j2html.tags.specialized.HrTag;
import jakarta.mail.MessagingException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.internal.entities.UserImpl;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.EntityOption;
import net.neoforged.camelot.config.module.BanAppeals;
import net.neoforged.camelot.configuration.OAuthUtils;
import net.neoforged.camelot.db.schemas.BanAppeal;
import net.neoforged.camelot.db.schemas.BanAppealBlock;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.BanAppealsDAO;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.server.WebServer;
import net.neoforged.camelot.util.DateUtils;
import net.neoforged.camelot.util.MailService;
import net.neoforged.camelot.util.oauth.OAuthClient;
import net.neoforged.camelot.util.oauth.OAuthScope;
import net.neoforged.camelot.util.oauth.TokenResponse;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static j2html.TagCreator.*;

/**
 * Module responsible for ban appeals.
 * <p>
 * OAuth tokes are stored in a {@code discord_token} cookie.
 * <p>
 * The flow is as follows:
 * <ul>
 *     <li>User accesses {@code /ban-appeals/:serverId}. If they're banned, proceed</li>
 *     <li>User receives:
 *     <ul>
 *         <li>- a form to submit an appeal if they have no appeal in progress</li>
 *         <li>- a form to submit a response if they have an appeal in progress and have received a follow-up question. When they submit a response, it is mirrored in Discord, and the 'followup' state is cleared to allow for another followup</li>
 *         <li>- a message to wait if no followup was sent and they have an appeal in progress</li>
 *     </ul>
 *     </li>
 * </ul>
 */
@RegisterCamelotModule
public class BanAppealModule extends CamelotModule.Base<BanAppeals> {

    private final ConfigOption<Guild, Set<Long>> appealsChannel;

    public BanAppealModule(ModuleProvider.Context context) {
        super(context, BanAppeals.class);
        accept(WebServerModule.SERVER, javalin -> {
            javalin.get("/ban-appeals/discord", this::verifyOauth);
            javalin.get("/ban-appeals/<serverId>", this::onAccess);
            javalin.post("/ban-appeals/followup/<serverId>", this::onSubmitFollowup);
            javalin.post("/ban-appeals/<serverId>", this::onSubmitAppeal);
        });

        var reg = context.guildConfigs();
        reg.setGroupDisplayName("Ban Appeals");
        appealsChannel = reg.option("appeals_channel", EntityOption.builder(EntitySelectMenu.SelectTarget.CHANNEL))
                .setMaxValues(1)
                .setDisplayName("Appeals channel")
                .setDescription("The channel ban appeals are sent to.", "If left unconfigured, ban appeals are disabled for this server.")
                .register();
    }

    private OAuthClient client;
    private MailService mail;

    @Override
    public String id() {
        return "ban-appeal";
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of("webserver");
    }

    @Override
    public void setup(JDA jda) {
        client = OAuthUtils.discord(config().getDiscordAuth()).fork(() -> BotMain.getModule(WebServerModule.class).makeLink("/ban-appeals/discord"), OAuthScope.Discord.EMAIL, OAuthScope.Discord.IDENTIFY);
        mail = MailService.from(config().getMail());
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners((EventListener) (GenericEvent gevent) -> {
            if (gevent instanceof ButtonInteractionEvent event) {
                onButton(event);
            } else if (gevent instanceof ModalInteractionEvent event) {
                onModal(event);
            }
        });
    }

    @Nullable
    private VCReturn verifyContext(Context context) throws Exception {
        if (context.cookie("discord_token") == null) {
            context.result(new JSONObject().put("error", "Missing token in cookie").toString())
                    .status(HttpStatus.UNAUTHORIZED);
            return null;
        }

        final String guildId = context.pathParam("serverId");
        final Guild guild = BotMain.get().getGuildById(guildId);
        if (guild == null || appealsChannel.get(guild).isEmpty()) {
            context.result(new JSONObject().put("error", "Unknown server").toString())
                    .status(HttpStatus.NOT_FOUND);
            return null;
        }

        final JSONObject self = BotMain.HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me"))
                        .header("Authorization", "Bearer " + context.cookie("discord_token")).build(),
                OAuthClient.jsonObject()
        ).body();
        final long selfId = Long.parseLong(self.getString("id"));

        return new VCReturn(guild, selfId);
    }

    record VCReturn(Guild guild, long selfId) {

    }

    private void onSubmitFollowup(Context context) throws Exception {
        final var verify = verifyContext(context);
        if (verify == null) return;

        final long selfId = verify.selfId;
        final Guild guild = verify.guild;

        final BanAppeal existing = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getAppeal(guild.getIdLong(), selfId));
        if (existing == null) {
            context.result(new JSONObject().put("error", "No appeal in progress").toString())
                    .status(HttpStatus.NOT_FOUND);
            return;
        }

        if (existing.currentFollowup() == null) {
            context.result(new JSONObject().put("error", "No followup was sent").toString())
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        final EmbedBuilder embed = new EmbedBuilder();
        final JSONObject payload = new JSONObject(context.body());

        if (payload.getString("response").length() > 3900) {
            context.result(new JSONObject().put("error", "Response too long").toString())
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        final User user = BotMain.get().retrieveUserById(selfId).complete();

        embed.setAuthor(user.getEffectiveName(), null, user.getEffectiveAvatarUrl())
                .setFooter("User ID: " + user.getId())
                .setTimestamp(Instant.now())
                .setTitle("User replied to follow-up")
                .addField("Follow-up", existing.currentFollowup(), false)
                .setDescription(payload.getString("response"))
                .setColor(Color.CYAN);

        guild.getChannelById(GuildMessageChannel.class, appealsChannel.get(guild).iterator().next()).retrieveMessageById(existing.threadId())
                .map(Message::getStartedThread)
                .flatMap(thread -> thread.sendMessageEmbeds(embed.build())
                        .and(thread.retrieveParentMessage()
                                .flatMap(msg -> msg.editMessageEmbeds(modifyColour(msg.getEmbeds().getFirst(), config().getColors().getOngoing())))))
                .queue();

        Database.appeals().useExtension(BanAppealsDAO.class, db -> db.setFollowup(
                guild.getIdLong(), selfId, null
        ));

        sendMailFromServer(existing.email(), user, guild, "Ban appeal follow-up",
                pre("We have received your follow-up."),
                pre("You should be informed of our decision within " + config().getResponseTime() + " days."),
                hr(),
                h4("Follow-up content"),
                h5("Our question"),
                pre(existing.currentFollowup()),
                h5("Your response"),
                pre(payload.getString("response")));

        context.status(HttpStatus.OK);
    }

    private void sendMailFromServer(String email, User user, Guild guild, String subject, DomContent... dc) {
        final var body = body(
                h4(text("Hi "), span(user.getEffectiveName()).withStyle("color: red")));
        boolean post = false;
        final DomContent[] kr = new DomContent[] {
                pre("Kind regards,"), pre(guild.getName()), img().withSrc(guild.getIconUrl()).attr("width", "32").attr("height", "32")
        };
        for (final DomContent dom : dc) {
            if (dom instanceof HrTag) {
                post = true;
                body.with(kr);
            }
            body.with(dom);
        }
        if (!post) {
            body.with(kr);
        }
        try {
            mail.sendHtml(config().getMail().getSendAs(), email, subject, html(body));
        } catch (MessagingException e) {
            BotMain.LOGGER.error("Failed to send mail to {}: ", email, e);
        }
    }

    private void onSubmitAppeal(Context context) throws Exception {
        final var verify = verifyContext(context);
        if (verify == null) return;
        final Guild guild = verify.guild;

        final long selfId = verify.selfId;

        final BanAppealBlock block = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getBlock(guild.getIdLong(), selfId));
        if (block != null) {
            final Instant now = Instant.now();
            if (block.expiration().isAfter(now)) {
                context.result(new JSONObject().put("error", "You cannot appeal for " + (block.expiration().toEpochMilli() - now.toEpochMilli()) + " milliseconds.").toString())
                        .status(HttpStatus.CONFLICT);
                return;
            } else {
                Database.appeals().useExtension(BanAppealsDAO.class, db -> db.deleteBlock(guild.getIdLong(), selfId));
            }
        }

        final BanAppeal existing = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getAppeal(guild.getIdLong(), selfId));
        if (existing != null) {
            context.result(new JSONObject().put("error", "Appeal already in-process").toString())
                    .status(HttpStatus.CONFLICT);
            return;
        }

        final var possibleBan = guild.retrieveBan(UserSnowflake.fromId(selfId))
                .mapToResult()
                .complete();
        if (possibleBan.isFailure()) {
            context.result(new JSONObject().put("error", "Not banned.").toString())
                    .status(HttpStatus.FORBIDDEN);
            return;
        }

        final EmbedBuilder embed = new EmbedBuilder();
        final JSONObject payload = new JSONObject(context.body());

        final User user = BotMain.get().retrieveUserById(selfId).complete();

        if (payload.getString("reason").length() > 3900) {
            context.result(new JSONObject().put("error", "Reason too long").toString())
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        embed.setAuthor(user.getEffectiveName(), null, user.getEffectiveAvatarUrl())
                .setFooter("User ID: " + user.getId())
                .setTimestamp(Instant.now())
                .setDescription("User appealed their ban for **" + Objects.requireNonNullElse(possibleBan.get().getReason(), "No reason given").replace("rec: ", "") + "**:\n")
                .appendDescription(payload.getString("reason"))
                .setColor(config().getColors().getOngoing());

        if (!payload.isNull("feedback") && !payload.getString("feedback").isBlank()) {
            if (payload.getString("feedback").length() > 1000) {
                context.result(new JSONObject().put("error", "Feedback too long").toString())
                        .status(HttpStatus.BAD_REQUEST);
                return;
            }

            embed.addField("Feedback", payload.getString("feedback"), false);
        }

        final ThreadChannel thread = BotMain.get().getChannelById(MessageChannel.class, appealsChannel.get(guild).iterator().next())
                .sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.success("ban-appeals/approve/" + selfId, "Approve"),
                        Button.danger("ban-appeals/reject/" + selfId, "Reject")
                ))
                .addComponents(ActionRow.of(Button.secondary("ban-appeals/followup/" + selfId, "Follow up with a question")))
                .flatMap(msg -> msg.createThreadChannel("Discussion of appeal of " + user.getEffectiveName()))
                .complete();

        Database.appeals().useExtension(BanAppealsDAO.class, db -> db.insertAppeal(
                guild.getIdLong(), selfId, payload.getString("email"), thread.getIdLong()
        ));

        sendMailFromServer(payload.getString("email"), user, guild, "Ban appeal",
                pre("We have received your ban appeal and we will send updates to this email going forward. Please do not reply to this email or any further ones."),
                pre("You should be informed of our decision within " + config().getResponseTime() + " days."),
                hr(),
                h4("Appeal content"),
                h5("Your explanation"),
                pre(payload.getString("reason")),
                h5("Your feedback"),
                pre((payload.isNull("feedback") || payload.getString("feedback").isBlank()) ? i("Not provided") : text(payload.getString("feedback"))));

        context.status(HttpStatus.OK);
    }

    private void onModal(ModalInteractionEvent event) {
        final String id = event.getModalId();
        if (!id.startsWith("ban-appeals/")) return;
        final String[] split = id.split("/");

        final long userId = Long.parseLong(split[2]);
        final var appeal = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getAppeal(event.getGuild().getIdLong(), userId));
        if (appeal == null) {
            event.reply("Invalid appeal!").setEphemeral(true).queue();
            return;
        }

        final var retrieveUser = event.getJDA().retrieveUserById(userId);
        final var guild = event.getGuild();
        assert guild != null;

        final ThreadChannel thread = event.getMessage().getStartedThread();
        assert thread != null;

        switch (split[1]) {
            case "followup" -> {
                final String replyText = event.getValue("reply").getAsString();
                Database.appeals().useExtension(BanAppealsDAO.class, db -> db.setFollowup(event.getGuild().getIdLong(), userId, replyText));

                thread.retrieveParentMessage()
                        .flatMap(msg -> msg.editMessageEmbeds(modifyColour(msg.getEmbeds().getFirst(), config().getColors().getPendingReply())))
                        .queue();

                retrieveUser.onSuccess(user -> sendMailFromServer(appeal.email(), user, guild, "Ban appeal follow-up",
                        pre(text("The moderators of "), b(event.getGuild().getName()), text(" have sent you a message:")),
                        pre(code(replyText)),
                        pre("You can visit " + BotMain.getModule(WebServerModule.class).makeLink("/ban-appeals/" + event.getGuild().getId()) + " to send a reply. Please do not reply to this email.")
                ))
                        .flatMap(_ -> event.reply("Reply sent.").setEphemeral(true))
                        .flatMap(_ -> event.getMessage().getStartedThread().sendMessageEmbeds(new EmbedBuilder()
                                .setAuthor(event.getUser().getName(), null, event.getMember().getEffectiveAvatarUrl())
                                .setTitle("Follow-up sent")
                                .setDescription(replyText)
                                .build()))
                        .queue();
            }

            case "reject" -> {
                final String reason = event.getValue("reason").getAsString();
                final String blockDaysStr = Optional.ofNullable(event.getValue("blockdays")).map(ModalMapping::getAsString)
                        .orElse("0");
                final Integer blockDays = Ints.tryParse(blockDaysStr);
                if (blockDays == null || blockDays < 0) {
                    event.reply("Block days must be a (positive) integer.").setEphemeral(true).queue();
                    return;
                }

                final Instant until = Instant.now().plus(blockDays, ChronoUnit.DAYS);
                Database.appeals().useExtension(BanAppealsDAO.class, db -> db.blockUntil(guild.getIdLong(), userId, "Previous appeal denied: " + reason, until.toEpochMilli()));

                event.deferReply(true)
                        .flatMap(_ -> thread.sendMessage("Ban appeal **rejected** by " + event.getUser().getAsMention() + ". Reason: **" + reason + "**."))
                        .flatMap(_ -> closeAppeal(thread, true))
                        .flatMap(_ -> retrieveUser)
                        .onSuccess(user -> sendMailFromServer(appeal.email(), user, guild, "Ban appeal rejected",
                                pre(text("Your appeal has been "), b(text("rejected")), text(".")),
                                pre(text("You may appeal again in "), b(text(blockDaysStr)), text(" days.")),
                                hr(),
                                h5("Reason"),
                                pre(reason)
                        ))
                        .onSuccess(_ -> Database.appeals().useExtension(BanAppealsDAO.class, db -> db.deleteAppeal(appeal.guildId(), appeal.userId())))
                        .flatMap(_ -> event.getInteraction().getHook().editOriginal("Appeal rejected."))
                        .queue();
            }

            case "approve" -> {
                var message = Optional.ofNullable(event.getValue("message"))
                        .map(ModalMapping::getAsString).filter(Predicate.not(String::isBlank))
                        .orElse(null);

                event.deferReply(true).flatMap(_ ->
                                bot().moderation().unban(
                                        event.getGuild(),
                                        UserSnowflake.fromId(userId),
                                        event.getUser(),
                                        "Ban appeal approved in thread: " + thread.getId()
                                ))
                        .flatMap(_ -> thread.sendMessage("Ban appeal **approved** by " + event.getUser().getAsMention() + (message == null ? "" : ". Message: **" + message + "**.")))
                        .flatMap(_ -> closeAppeal(thread, false))
                        .flatMap(_ -> retrieveUser)
                        .flatMap(user -> event.getGuild().getDefaultChannel().createInvite()
                                .setMaxUses(1).setMaxAge((long) 7, TimeUnit.DAYS)
                                .reason("Un-ban invite for " + userId)
                                .onSuccess(invite -> sendMailFromServer(appeal.email(), user, event.getGuild(), "Ban appeal approved",
                                        pre(text("Your appeal has been "), b(text("approved")), text(".")),
                                        pre(text("You may join the server again using "), a("this invite link").withHref(invite.getUrl()), text(" which expires in 7 days.")),
                                        div().condWith(message != null, hr(),
                                                h5("Message from moderators"),
                                                pre(message))
                                )))
                        .onSuccess(_ -> Database.appeals().useExtension(BanAppealsDAO.class, db -> db.deleteAppeal(appeal.guildId(), appeal.userId())))
                        .flatMap(_ -> event.getInteraction().getHook().editOriginal("Appeal approved."))
                        .queue();
            }
        }
    }

    private void onButton(ButtonInteractionEvent event) {
        final String id = event.getButton().getCustomId();
        if (id == null || !id.startsWith("ban-appeals/")) return;
        final String[] split = id.split("/");

        final long userId = Long.parseLong(split[2]);
        final var appeal = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getAppeal(event.getGuild().getIdLong(), userId));
        if (appeal == null) {
            event.reply("Invalid appeal!").setEphemeral(true).queue();
            return;
        }

        assert event.getGuild() != null;

        switch (split[1]) {
            case "followup" -> event.replyModal(Modal.create("ban-appeals/followup/" + userId, "Send reply")
                            .addComponents(Label.of("Reply", TextInput.create("reply", TextInputStyle.PARAGRAPH).setRequired(true).build()))
                            .build())
                    .queue();
            case "reject" -> event.replyModal(Modal.create("ban-appeals/reject/" + userId, "Reject appeal")
                            .addComponents(Label.of("Reason", TextInput.create("reason", TextInputStyle.PARAGRAPH).setRequired(true).build()))
                            .addComponents(Label.of("Block days", "The amount of days to block the user from re-sending an appeal for", TextInput.create("blockdays", TextInputStyle.SHORT).setRequired(false).build()))
                            .build())
                    .queue();
            case "approve" -> event.replyModal(Modal.create("ban-appeals/approve/" + userId, "Approve appeal")
                    .addComponents(Label.of("Message", "Optional message sent to the appellee accompanying their invite to join the server", TextInput.create("message", TextInputStyle.PARAGRAPH).setRequired(false).build()))
                    .build())
                    .queue();
        }
    }

    private RestAction<?> closeAppeal(ThreadChannel thread, boolean rejected) {
        return thread.retrieveParentMessage()
                .flatMap(msg -> msg.editMessageComponents(List.of())
                        .setContent(rejected ? "Appeal rejected" : "Appeal approved")
                        .setEmbeds(modifyColour(msg.getEmbeds().getFirst(), rejected ? config().getColors().getRejected() : config().getColors().getApproved())))
                .flatMap(_ -> thread.getManager().setArchived(true));
    }

    private void onAccess(Context context) throws Exception {
        if (context.queryParam("success") != null) {
            if (context.queryParam("followup") != null) {
                context.html(WebServer.tag()
                                .withTitle(title("Thank you for your response"))
                                .withContent(div(h2("Response sent"), pre("The moderation team will get back to you in due time."), i().withClass("bi bi-check2").withStyle("font-size: 128px")).withClass("px-4 py-5 my-5 text-center"))
                                .create().render())
                        .status(HttpStatus.OK);
                return;
            }
            context.html(WebServer.tag()
                    .withTitle(title("Thank you for your appeal"))
                    .withContent(div(h2("Appeal submitted"), pre("The moderation team will get back to you in due time."), i().withClass("bi bi-check2").withStyle("font-size: 128px")).withClass("px-4 py-5 my-5 text-center"))
                    .create().render())
                    .status(HttpStatus.OK);
            return;
        }

        if (context.cookie("discord_token") == null) {
            context.redirect(client.getAuthorizationUrl(context.pathParam("serverId")), HttpStatus.TEMPORARY_REDIRECT);
            return;
        }

        final JSONObject self = BotMain.HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me"))
                        .header("Authorization", "Bearer " + context.cookie("discord_token")).build(),
                OAuthClient.jsonObject()
        ).body();
        final long selfId = Long.parseLong(self.getString("id"));

        final String guildId = context.pathParam("serverId");
        final Guild guild = BotMain.get().getGuildById(guildId);
        if (guild == null || appealsChannel.get(guild).isEmpty()) {
            context.html(WebServer.tag()
                    .withTitle(title("Unknown server"))
                    .withContent(div(h2("Unknown server!")).withClass("px-4 py-5 my-5 text-center"))
                    .create().render());
            return;
        }

        final WebServer.BaseRootTag tag = WebServer.tag()
                .withTitle(title("Ban appeal request"))
                .withHead(style().withText(".toast { transition: opacity 0.5s linear !important; }"))
                .withContent(
                        div(header(
                                img().withSrc(getAvatar(self, self.getString("discriminator")))
                                        .withClass("rounded-circle shadow-4").withAlt("Avatar")
                                        .attr("width", "32").attr("height", "32"),
                                span(self.getString("username")).withStyle("padding-left: 5px"),

                                span(" | ").withStyle("padding-left: 5px; padding-right: 5px"),

                                img().withSrc(guild.getIconUrl())
                                        .withClass("rounded-circle shadow-4").withAlt("Guild Icon")
                                        .attr("width", "32").attr("height", "32"),
                                span(guild.getName()).withStyle("padding-left: 5px")
                        ).withClass("d-flex flex-wrap justify-content-center py-3 mb-4 border-bottom")).withClass("container")
                );

        tag.withContent(div(div(
                div(
                        strong("Request error").withClass("me-auto"),
                        small("now").withClass("text-body-secondary"),
                        button().withType("button").withClass("btn-close").attr("data-bs-dismiss", "toast").attr("aria-label", "close")
                ).withClass("toast-header"),
                div().withId("responseToastBody").withClass("toast-body")
        ).withClass("toast").attr("role", "alert").withId("responseToast")).withClass("toast-container top-0 end-0"));

        final Instant now = Instant.now();
        final BanAppealBlock block = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getBlock(guild.getIdLong(), selfId));
        if (block != null && block.expiration().isAfter(now)) {
            tag.withContent(div(p(text("You cannot appeal for "), code(DateUtils.formatDuration(
                    Duration.between(now, block.expiration())
            )), text(".")), p(text("Reason: "), code(block.reason()))).withClass("px-4 py-5 my-5 text-center"));
            context.html(tag.create().render());
            return;
        }

        final BanAppeal existing = Database.appeals().withExtension(BanAppealsDAO.class, db -> db.getAppeal(guild.getIdLong(), selfId));
        if (existing != null) {
            if (existing.currentFollowup() == null) {
                tag.withContent(div(p(text("You already have an appeal ongoing. Updates will be sent to "), code(existing.email()), text("."))).withClass("px-4 py-5 my-5 text-center"));
                context.html(tag.create().render());
                return;
            }

            tag.withContent(div(
                    div(
                            h2("Ban appeal follow-up"),
                            p(text("You have received a follow-up question from the %s moderation team:".formatted(guild.getName()))).withClass("lead"),
                            br(),
                            p(code(existing.currentFollowup())).withClass("lead")
                    ).withClass("py-5 text-center"),
                    div(
                            div(
                                    h4("Reply").withClass("mb-3"),
                                    form(
                                            div(div(
                                                    label("Your response").withFor("response").withClass("form-label"),
                                                    input().withType("text").withMaxlength("3900").withClass("form-control").withId("response").withCondRequired(true)
                                            ).withClass("col-12")).withClass("row g-3"),
                                            hr().withClass("my-4"),
                                            button("Send response").withId("submitFollowup").withClass("w-100 btn btn-primary btn-lg").withType("submit")
                                    ).withId("followupForm")
                            ).withClass("col-md-7 col-lg-8")
                    ).withClass("row g-5")
            ).withClass("container"));

            tag.withContent(script().withType("text/javascript").withSrc("/static/script/banappeal-followup.js"));

            context.html(tag.create().render());

            return;
        }

        final Result<Guild.Ban> ban = guild.retrieveBan(UserSnowflake.fromId(selfId))
                .mapToResult()
                .complete();

        if (ban.isFailure()) {
            tag.withContent(div(
                    h3(text("You are not banned in "), span(guild.getName()).withStyle("font-weight: bold")),
                    span("Note: this form is for ban appeals specifically. For other inquiries (such as unfair treatment when warned or muted) please contact ModMail.")
            ).withClass("px-4 py-5 my-5 text-center"));
        } else {
            final var infractions = Database.main().withExtension(ModLogsDAO.class, db -> db.getLogs(
                    selfId, Long.parseLong(guildId), 0, Integer.MAX_VALUE, null, ModLogEntry.Type.NOTE
            ));
            Collections.reverse(infractions);

            final String banReason = Objects.requireNonNullElse(ban.get().getReason(), "No reason given.");
            tag.withContent(div(
                    div(
                            h2("Ban appeal form"),
                            p(text("If you feel you were banned without a good cause, please fill this form."), br(), text("The moderation team will consider your appeal and you will be contacted with the outcome of the decision")).withClass("lead"),
                            br(),
                            p(text("Ban reason: "), code(banReason.startsWith("rec: ") ? banReason.substring(5) : banReason)).withClass("lead")
                    ).withClass("py-5 text-center"),
                    div(
                            div(
                                    h4(
                                        span("Your infractions").withClass("text-primary"),
                                        span(String.valueOf(infractions.size())).withClass("badge bg-primary rounded-pill")
                                    ).withClass("d-flex justify-content-between align-items-center mb-3"),
                                    ul(each(infractions, entry -> {
                                        final var time = LocalDateTime.ofInstant(entry.timestamp(), ZoneOffset.UTC);
                                        return li(
                                                div(
                                                        h6(switch (entry.type()) {
                                                            case BAN -> "Banned";
                                                            case WARN -> "Warned";
                                                            case KICK -> "Kicked";
                                                            case MUTE -> "Muted";
                                                            case UNBAN -> "Unbanned";
                                                            case UNMUTE -> "Unmuted";
                                                            default -> entry.type().getAction();
                                                        }).withClass("my-0"),
                                                        small(Objects.requireNonNullElse(entry.reason(), "No reason given."))
                                                ),
                                                span(time.getDayOfMonth() + " " + StringUtils.capitalize(time.getMonth().toString().toLowerCase(Locale.ROOT)) + " " + time.getYear()).withClass("text-body-secondary")
                                        ).withClass("list-group-item d-flex justify-content-between lh-sm");
                                    })).withClass("list-group mb-3")
                            ).withClass("col-md-5 col-lg-4 order-md-last"),
                            div(
                                    h4("Appeal").withClass("mb-3"),
                                    form(
                                            div(div(
                                                    label("Your explanation for your appeal").withFor("unbanReason").withClass("form-label"),
                                                    input().withMaxlength("3900").withType("text").withClass("form-control").withId("unbanReason").withCondRequired(true)
                                            ).withClass("col-12")).withClass("row g-3"),
                                            hr().withClass("my-4"),
                                            div(
                                                   div(
                                                           label("Email to send updates to").withFor("replyEmail").withClass("form-label"),
                                                           input().withType("email").withClass("form-control").withId("replyEmail").withValue(self.getString("email")).withCondRequired(true)
                                                   ).withClass("col-sm-6"),
                                                   div(
                                                           label("Other thoughts").withFor("feedback").withClass("form-label"),
                                                           input().withMaxlength("1000").withType("input").withClass("form-control").withPlaceholder("Feedback on the actions taken").withId("feedback")
                                                   ).withClass("col-sm-6")
                                            ).withClass("row g-3"),
                                            hr().withClass("my-4"),
                                            button("Send appeal").withId("submitAppeal").withClass("w-100 btn btn-primary btn-lg").withType("submit")
                                    ).withId("banAppealForm")
                            ).withClass("col-md-7 col-lg-8")
                    ).withClass("row g-5")
            ).withClass("container"));

            tag.withContent(script().withType("text/javascript").withSrc("/static/script/banappeal.js"));
        }

        context.html(tag.create()
                .render());
    }

    private void verifyOauth(Context ctx) throws Exception {
        if (ctx.queryParam("code") == null) {
            ctx.redirect(client.getAuthorizationUrl(), HttpStatus.TEMPORARY_REDIRECT);
        } else {
            final TokenResponse token = client.getToken(ctx.queryParam("code"));
            ctx.cookie(new Cookie(
                    "discord_token",
                    token.accessToken(),
                    "/ban-appeals",
                    (int) (token.expiration().getEpochSecond() - Instant.now().getEpochSecond()),
                    false
            ));
            ctx.redirect("/ban-appeals/" + ctx.queryParam("state"), HttpStatus.TEMPORARY_REDIRECT);
        }
    }

    /**
     * Gets the avatar URL of an user based on its discriminator and ID.
     * Similar to {@link UserImpl#getAvatarUrl()} and {@link UserImpl#getAvatarId()}
     */
    private static String getAvatar(JSONObject author, String discriminator) {
        String avatar = author.optString("avatar", null);
        final String id = author.optString("id");
        if (avatar == null) {
            avatar = discriminator.equals("0") ? String.valueOf((Long.parseLong(id) >> 22) % 5) : String.valueOf(Integer.parseInt(discriminator) % 5);
            return String.format("https://cdn.discordapp.com/embed/avatars/%s.png", avatar);
        }
        return String.format("https://cdn.discordapp.com/avatars/%s/%s.%s", id, avatar, avatar.startsWith("a_") ? "gif" : "png");
    }

    private static MessageEmbed modifyColour(MessageEmbed embed, int colour) {
        return new EmbedBuilder(embed).setColor(colour).build();
    }
}
