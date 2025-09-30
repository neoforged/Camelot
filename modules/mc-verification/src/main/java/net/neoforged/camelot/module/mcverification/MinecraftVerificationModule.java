package net.neoforged.camelot.module.mcverification;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import j2html.tags.DomContent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.config.module.MinecraftVerification;
import net.neoforged.camelot.configuration.OAuthUtils;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.BanAppealModule;
import net.neoforged.camelot.module.LoggingModule;
import net.neoforged.camelot.module.WebServerModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.mcverification.protocol.Crypt;
import net.neoforged.camelot.module.mcverification.protocol.MinecraftConnection;
import net.neoforged.camelot.module.mcverification.protocol.MinecraftServerVerificationHandler;
import net.neoforged.camelot.server.WebServer;
import net.neoforged.camelot.util.DateUtils;
import net.neoforged.camelot.util.Utils;
import net.neoforged.camelot.util.oauth.OAuthClient;
import net.neoforged.camelot.util.oauth.OAuthScope;
import net.neoforged.camelot.util.oauth.TokenResponse;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static j2html.TagCreator.a;
import static j2html.TagCreator.br;
import static j2html.TagCreator.button;
import static j2html.TagCreator.code;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.hr;
import static j2html.TagCreator.i;
import static j2html.TagCreator.li;
import static j2html.TagCreator.p;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;
import static j2html.TagCreator.text;
import static j2html.TagCreator.title;
import static j2html.TagCreator.ul;

@AutoService(CamelotModule.class)
public class MinecraftVerificationModule extends CamelotModule.WithDatabase<MinecraftVerification> {
    private OAuthClient microsoft, discord;

    public MinecraftVerificationModule() {
        super(MinecraftVerification.class);
        accept(WebServerModule.SERVER, javalin -> {
            javalin.get("/minecraft/<serverId>/verify", this::onVerifyRoot);
            javalin.post("/minecraft/<serverId>/verify", this::onVerifyPost);
            javalin.get("/minecraft/verify/discord", ctx -> verifyOauth(ctx, "discord_token", discord));
            javalin.get("/minecraft/verify/microsoft", ctx -> verifyOauth(ctx, "xbox_token", microsoft));
            javalin.get("/minecraft/verify/info", this::onInfo);
            javalin.get("/minecraft/<serverId>/verify/clear", ctx -> {
                final String path = "/minecraft/" + ctx.pathParam("serverId") + "/verify";
                ctx.removeCookie("discord_token", path);
                ctx.removeCookie("xbox_token", path);
            });
        });
    }

    @Override
    public String id() {
        return "mc-verification";
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of("webserver");
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new VerifyMCCommand(db().onDemand(McVerificationDAO.class)));
    }

    @Override
    public void setup(JDA jda) {
        discord = OAuthUtils.discord(config().getDiscordAuth()).fork(() -> BotMain.getModule(WebServerModule.class).makeLink("/minecraft/verify/discord"), OAuthScope.Discord.IDENTIFY);
        if (config().getMicrosoftAuth() != null) {
            microsoft = OAuthUtils.microsoft(config().getMicrosoftAuth()).fork(() -> BotMain.getModule(WebServerModule.class).makeLink("/minecraft/verify/microsoft"), OAuthScope.Microsoft.XBOX_LIVE);
        }

        final McVerificationDAO dao = db().onDemand(McVerificationDAO.class);
        BotMain.EXECUTOR.scheduleAtFixedRate(() -> banNotVerified(jda, dao), 1, 1, TimeUnit.MINUTES);

        if (config().getMinecraftServerPort() != 0) {
            final ExecutorService mcExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                    .name("mc-verification-server-", 0)
                    .uncaughtExceptionHandler((_, ex) -> BotMain.LOGGER.error("Error while handing server-based MC verification", ex))
                    .factory());
            setupMinecraftServer(config().getMinecraftServerPort(), mcExecutor);
        }
    }

    private void banNotVerified(JDA jda, McVerificationDAO db) {
        for (final Guild guild : jda.getGuilds()) {
            final List<Long> users = db.getUsersToBan(guild.getIdLong());
            if (!users.isEmpty()) {
                for (final long toBan : users) {
                    // We do not use allOf because we do not want a deleted user to cause all unbans to fail
                    jda.retrieveUserById(toBan)
                            .flatMap(user -> Utils.attemptDM(user, pc -> {
                                var message = new EmbedBuilder()
                                        .setAuthor(guild.getName(), null, guild.getIconUrl())
                                        .setDescription("You have been **banned** in **" + guild.getName() + "**.")
                                        .addField("Reason", "Failed to verify Minecraft account ownership", false)
                                        .addField("Duration", DateUtils.formatDuration(config().getBanDuration()), false)
                                        .setColor(ModLogEntry.Type.BAN.getColor())
                                        .setTimestamp(Instant.now());

                                if (BotMain.getModule(BanAppealModule.class) != null) {
                                    message.appendDescription("\nYou may appeal the ban at " + BotMain.getModule(WebServerModule.class)
                                            .makeLink("/ban-appeals/" + guild.getId()) + ".");
                                }

                                return pc.sendMessageEmbeds(message.build());
                            }))

                            .flatMap(_ -> guild.ban(UserSnowflake.fromId(toBan), 0, TimeUnit.MINUTES).reason("rec: Failed to verify Minecraft account ownership"))
                            .queue(_ -> db.delete(guild.getIdLong(), toBan), new ErrorHandler()
                                    .handle(ErrorResponse.UNKNOWN_USER, _ -> db.delete(toBan, guild.getIdLong()))); // User doesn't exist, so don't care about the ban anymore

                    Database.main().useExtension(PendingUnbansDAO.class, pending -> pending.insert(
                            toBan, guild.getIdLong(), Timestamp.from(Instant.now().plus(config().getBanDuration()))
                    ));

                    ModerationActionRecorder.recordAndLog(
                            ModLogEntry.ban(toBan, guild.getIdLong(), guild.getSelfMember().getIdLong(), config().getBanDuration(), "Failed to verify Minecraft account ownership"),
                            jda
                    );
                }
            }
        }
    }

    private void setupMinecraftServer(int port, ExecutorService executor) {
        ServerSocket socket;
        try {
            socket = new ServerSocket(port);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        var keyPair = Crypt.generateKeyPair();
        var authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY);

        executor.submit(() -> {
            while (true) {
                var client = socket.accept();
                var handler = new MinecraftServerVerificationHandler(new MinecraftConnection(client, keyPair), authService, keyPair, this::handleServerVerification);
                executor.submit(handler);
            }
        });
    }

    private String handleServerVerification(String serverAddress, GameProfile profile) {
        var addressPattern = Pattern.compile(config().getMinecraftServerAddress().split(":")[0]
                .replace("<token>", "([a-z0-9]+)")
                .replace(".", "\\."));
        var matcher =  addressPattern.matcher(serverAddress);
        if (!matcher.matches()) {
            return "Server address incompatible. Contact server moderators for assistance";
        }

        var token = matcher.group(1);
        var userInfo = db().withExtension(McVerificationDAO.class, db -> db.getByServerJoinToken(token));
        if (userInfo == null) {
            return "Unknown verification token (" + token + "). Verification may no longer be needed";
        }

        var guild = BotMain.get().getGuildById(userInfo.guild());
        if (guild == null) {
            return "Unknown guild " + userInfo.guild();
        }

        final var verificationInfo = db().withExtension(McVerificationDAO.class, db -> db.getVerificationInformation(userInfo.guild(), userInfo.user()));
        if (verificationInfo == null) {
            return "Verification not needed";
        }

        db().useExtension(McVerificationDAO.class, db -> db.delete(userInfo.guild(), userInfo.user()));

        var user = guild.getJDA()
                .retrieveUserById(userInfo.user())
                .submit()
                .join();

        finishVerification(guild, userInfo.user(), verificationInfo.message(), profile.name(), profile.id());

        return "Ownership for §6@" + user.getName() + "§r verified as §b" + profile.name() + "§r\nYou have been unmuted and may return to the server";
    }

    private void verifyOauth(Context ctx, String cookieName, OAuthClient client) throws Exception {
        if (ctx.queryParam("code") == null) {
            ctx.redirect(client.getAuthorizationUrl(ctx.queryParam("state")), HttpStatus.TEMPORARY_REDIRECT);
        } else {
            final TokenResponse token = client.getToken(ctx.queryParam("code"));
            ctx.cookie(new Cookie(
                    cookieName,
                    token.accessToken(),
                    "/minecraft/" + ctx.queryParam("state") + "/verify",
                    (int) (token.expiration().getEpochSecond() - Instant.now().getEpochSecond()),
                    false
            ));
            ctx.redirect("/minecraft/" + ctx.queryParam("state") + "/verify", HttpStatus.TEMPORARY_REDIRECT);
        }
    }

    @Nullable
    private BaseMinecraftProfile acquireMCProfile(String xboxToken) throws Exception {
        var data = new JSONObject();
        var props = new JSONObject();
        props.put("AuthMethod", "RPS");
        props.put("SiteName", "user.auth.xboxlive.com");
        props.put("RpsTicket", "d=" + xboxToken);
        data.put("Properties", props);
        // This has to be http because Microsoft is great
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        var request = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-xbl-contract-version", "1")
                .POST(HttpRequest.BodyPublishers.ofString(data.toString())).build();
        var resp = BotMain.HTTP_CLIENT.send(request, OAuthClient.jsonObject()).body();
        return acquireXsts(resp.getString("Token"));
    }

    private JSONObject getUser(String discordToken) throws Exception {
        return BotMain.HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me"))
                        .header("Authorization", "Bearer " + discordToken).build(),
                OAuthClient.jsonObject()
        ).body();
    }

    private void onVerifyPost(Context context) throws Exception {
        final long serverId = Long.parseLong(context.pathParam("serverId"));
        final Guild guild = BotMain.get().getGuildById(serverId);
        if (guild == null) {
            context.result(new JSONObject().put("error", "Unknown guild").toString())
                    .status(HttpStatus.NOT_FOUND);
            return;
        }

        final String discordToken = context.cookie("discord_token");
        final String xboxToken = context.cookie("xbox_token");
        final long userId = getUser(discordToken).getLong("id");

        final var verificationInfo = db().withExtension(McVerificationDAO.class, db -> db.getVerificationInformation(serverId, userId));
        if (verificationInfo == null) {
            context.result(new JSONObject().put("error", "Verification not needed").toString())
                    .status(HttpStatus.UNAUTHORIZED);
            return;
        }

        final var profile = acquireMCProfile(xboxToken);
        if (profile == null) {
            context.result(new JSONObject().put("error", "No Minecraft account linked").toString())
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        db().useExtension(McVerificationDAO.class, db -> db.delete(serverId, userId));
        context.status(HttpStatus.OK);
        context.removeCookie("xbox_token");
        context.removeCookie("discord_token");

        finishVerification(guild, userId, verificationInfo.message(), profile.name, profile.uuid);
    }

    private void finishVerification(Guild guild, long userId, String targetMessage, String name, UUID uuid) {
        ReferencingListener.decodeMessageLink(targetMessage)
                .flatMap(msg -> msg.retrieve(BotMain.get()))
                .ifPresent(m -> m.flatMap(msg -> msg.reply("Ownership verified as [" + name + "](<https://mcuuid.net/?q=" + uuid + ">)!").setAllowedMentions(List.of()))
                        .flatMap(_ -> guild.removeTimeout(UserSnowflake.fromId(userId)).reason("Minecraft ownership verified"))
                        .flatMap(_ -> guild.getJDA().retrieveUserById(userId))
                        .flatMap(user -> Utils.attemptDM(user, action -> action.sendMessageEmbeds(new EmbedBuilder()
                                .setAuthor(guild.getName(), null, guild.getIconUrl())
                                .setDescription("You have verified the ownership of a Minecraft account, and as such have been **un-muted** in **" + guild.getName() + "**.")
                                .setColor(ModLogEntry.Type.UNMUTE.getColor())
                                .setTimestamp(Instant.now())
                                .build())))
                        .onSuccess(_ -> LoggingModule.MODERATION_LOGS.log(new EmbedBuilder()
                                .setTitle("Verify Minecraft")
                                .setColor(Color.GREEN)
                                .setDescription("<@" + userId + "> has verified that they own a Minecraft Account")
                                .setTimestamp(Instant.now())
                                .addField("Profile", "[" + name + "](https://mcuuid.net/?q=" + uuid + ")", true)
                                .setFooter("User ID: " + userId)
                                .build()))
                        .queue());
    }

    private void onVerifyRoot(Context context) throws Exception {
        if (context.queryParam("success") != null) {
            context.html(WebServer.tag()
                            .withTitle(title("MC ownership verified"))
                            .withContent(div(h2("You have verified that you own a Minecraft account. You may go back to the server now.")).withClass("px-4 py-5 my-5 text-center"))
                            .create().render())
                    .status(HttpStatus.OK);
            return;
        }

        final long serverId = Long.parseLong(context.pathParam("serverId"));
        final String discordToken;
        String xboxToken;
        if (context.queryParam("reset") != null) {
            context.removeCookie("discord_token", "/minecraft/" + serverId + "/verify");
            discordToken = null;
            context.removeCookie("xbox_token", "/minecraft/" + serverId + "/verify");
            xboxToken = null;
        } else {
            discordToken = context.cookie("discord_token");
            xboxToken = context.cookie("xbox_token");
        }

        @Nullable
        final McVerificationDAO.VerificationInformation verificationInfo;

        final DomContent discordStatus;
        if (discordToken == null) {
            discordStatus = span(span(" Please link your Discord account"));
            verificationInfo = null;
        } else {
            final JSONObject self = getUser(discordToken);
            verificationInfo = db().withExtension(McVerificationDAO.class, db -> db.getVerificationInformation(serverId, self.getLong("id")));
            if (verificationInfo == null) {
                context.html(WebServer.tag()
                                .withTitle(title("You do not need to verify Minecraft ownership"))
                                .withContent(div(h2("You do not need to verify Minecraft ownership")).withClass("px-4 py-5 my-5 text-center"))
                                .create().render())
                        .status(HttpStatus.OK);
                return;
            }

            discordStatus = span(span(" Logged as "), i(self.getString("username")));
        }

        final DomContent xboxStatus;
        if (xboxToken == null) {
            xboxStatus = span(span(" Link your Microsoft account"));
        } else {
            final var profile = acquireMCProfile(xboxToken);
            if (profile == null) {
                xboxStatus = span("The linked Microsoft account does not have a Minecraft profile. Please refresh this page and try again with a valid account");
                context.removeCookie("xbox_token");
                xboxToken = null;
            } else {
                xboxStatus = span(span(" Logged as "), i(profile.name()));
            }
        }

        final boolean canVerify = discordToken != null && xboxToken != null;

        var verificationOptions = new ArrayList<String>();

        final var verifyElements = new ArrayList<DomContent>();
        verifyElements.add(div().withStyle("height: 3em"));

        if (config().getMicrosoftAuth() != null) {
            verificationOptions.add("log in with your Microsoft account");
            verifyElements.add(a(i().withClasses("bi", "bi-microsoft"), xboxStatus).withCondHref(xboxToken == null, microsoft.getAuthorizationUrl(serverId))
                    .withCondClass(xboxToken == null, "btn btn-lg px-4 gap-3 btn-primary")
                    .withCondClass(xboxToken != null, "btn btn-lg px-4 gap-3 btn-success disabled"));
        }

        if (config().getMicrosoftAuth() != null && config().getMinecraftServerPort() != 0 && verificationInfo != null) {
            verifyElements.add(br());
            verifyElements.add(span("OR").withStyle("font-size: 200%").withClass("p-3"));
            verifyElements.add(br());
        }

        if (config().getMinecraftServerPort() != 0 && verificationInfo != null) {
            verificationOptions.add("join the Minecraft server below");
            verifyElements.add(div(
                    p(text("Join the Minecraft server at the following address: "), span(code(config().getMinecraftServerAddress().replace("<token>", verificationInfo.serverJoinToken())).withClass("p-1"))
                            .withClass("border")).withClass("lead mb-1"),
                    p("You can use any version of Minecraft, modded or otherwise.").withStyle("font-size: 100%").withClass("lead mb-1")
            ));
        }

        if (config().getMicrosoftAuth() != null) {
            verifyElements.add(hr());
            verifyElements.add(div(
                    button("Verify").withType("button").withId("verify").withClasses("bi btn btn-lg px-4 gap-3", canVerify ? "btn-primary" : "btn-secondary disabled")
                            .withTitle(canVerify ? "Verify your Minecraft account" : "Please connect win Discord and Microsoft first")
            ).withClass("d-grid gap-2 d-sm-flex justify-content-sm-center"));
        }

        context.html(WebServer.tag()
                .withTitle(title("Minecraft Account Verification"))
                .withHead(script().withType("text/javascript").withSrc("/static/script/mcverify.js"))
                .withBody(b -> b.withOnload("onLoad()"))
                .withContent(div(
                        h1("Minecraft Account Verification").withClass("display-5 fw-bold text-body-emphasis"),
                        div().withClass("col-lg-6 mx-auto"),
                        p(text("In order to verify that you own a Minecraft account, please log in with your Discord Account and then " + String.join(" or ", verificationOptions)),
                                br(),
                                a("How does it work?").withClass("text-reset fw-bold").withHref("/minecraft/verify/info")).withClass("lead mb-4"),
                        a(i().withClasses("bi", "bi-discord"), discordStatus).withCondHref(discordToken == null, discord.getAuthorizationUrl(serverId))
                                .withCondClass(discordToken == null, "btn btn-lg px-4 gap-3 btn-primary")
                                .withCondClass(discordToken != null, "btn btn-lg px-4 gap-3 btn-success disabled"),
                        div(verifyElements.toArray(DomContent[]::new)).withCondHidden(discordToken == null)
                ).withClass("px-4 py-5 my-5 text-center container"))
                .create()
                .render());
    }

    private void onInfo(final Context context) {
        context.html(WebServer.tag()
                .withTitle(title("How MC verification works"))
                .withContent(div(
                        h2("How Minecraft verification works"),
                        p("Piracy is not tolerated, and as such, to comply with the Discord Terms of Service, moderators may ask you to verify that you own a Minecraft account."),
                        p("This process is quick and can be done on mobile too."),
                        p("First, connect to Discord. We only request to be able to identify you so that we know your user ID. We cannot access your payment information or your email."),
                        p("Second, you will be given one or both of the options below:"),
                        ul(
                                li("Connect to the Microsoft account your Minecraft account is linked to. We will not be given access to any personal or sensitive information when you connect. If the Microsoft account has a Minecraft account, you will be able to click the Verify button."),
                                li("Launch any version of Minecraft (vanilla or modded) and attempt to join the server at the given address.")
                        ),
                        p("After verifying that you own Minecraft using one of the aforementioned options you may go back to the server and continue what you were doing!"),
                        h5("How are the OAuth tokens stored?"),
                        p("The OAuth tokens are stored as cookies. After you verify, the cookies will be deleted. We do not store the tokens on our servers.")
                ).withClass("px-4 py-5 my-5 container"))
                .create()
                .render());
    }

    @Nullable
    private static BaseMinecraftProfile acquireXsts(String xblToken) throws Exception {
        var data = new JSONObject();
        var props = new JSONObject();
        var userTokens = new JSONArray();
        userTokens.put(xblToken);
        props.put("SandboxId", "RETAIL");
        props.put("UserTokens", userTokens);
        data.put("Properties", props);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        var request = HttpRequest.newBuilder(XSTS_AUTH_URI)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(data.toString())).build();

        var resp = BotMain.HTTP_CLIENT.send(request, OAuthClient.jsonObject());
        var json = resp.body();

        if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
            if (!json.has("Token") || !json.has("DisplayClaims")) {
                throw new BadRequestResponse("Minecraft link error C1: " + json.toString(2));
            }

            var xblXsts = json.getString("Token");
            var claims = json.getJSONObject("DisplayClaims");

            if (!claims.has("xui")) {
                throw new BadRequestResponse("Minecraft link error C1: " + json.toString(2));
            }

            var xui = claims.getJSONArray("xui");

            if (xui.isEmpty() || !xui.getJSONObject(0).has("uhs")) {
                throw new BadRequestResponse("Minecraft link error C1: " + json.toString(2));
            }

            var uhs = xui.getJSONObject(0).getString("uhs");

            try {
                return acquireMinecraftToken(uhs, xblXsts);
            } catch (HttpTimeoutException ex) {
                throw new BadRequestResponse("Timeout while acquiring Minecraft token! It's possible that Minecraft auth servers are down, please try again in few hours!");
            }
        }

        String xerr = "";
        if (json.has("XErr")) {
            xerr = json.get("XErr").toString();
        }

        if (!xerr.isEmpty()) {
            switch (xerr) {
                case "2148916233" -> throw new BadRequestResponse("Microsoft account does not have an Xbox account");
                case "2148916235" -> throw new BadRequestResponse("Accounts from countries where XBox Live is not available or banned");
                case "2148916236" -> throw new BadRequestResponse("You must complete adult verification on the XBox homepage");
                case "2148916237" -> throw new BadRequestResponse("Age verification must be completed on the XBox homepage");
                case "2148916238" -> throw new BadRequestResponse("The account is under the age of 18, an adult must add the account to the family. You may need to check your e-mail.");
                default -> throw new BadRequestResponse("Xbox XSTS Authentication Error! Code: " + xerr);
            }
        }

        throw new BadRequestResponse("Minecraft link error C2 " + resp.statusCode() + ": " + json.toString(2));
    }

    public static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    public static final URI MC_XBOX_AUTH_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    public static final URI MC_PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    @Nullable
    private static BaseMinecraftProfile acquireMinecraftToken(String xblUhs, String xblXsts) throws Exception {
        var data = new JSONObject();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);

        var request = HttpRequest.newBuilder(MC_XBOX_AUTH_URI)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(data.toString())).build();

        var resp = BotMain.HTTP_CLIENT.send(request, OAuthClient.jsonObject());
        var json = resp.body();

        if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
            if (!json.has("access_token")) {
                throw new BadRequestResponse("Minecraft link error D1: " + json.toString(2));
            }

            var mcAccessToken = json.getString("access_token");
            return checkMcProfile(mcAccessToken);
        }

        if (json.getString("path").equals("/authentication/login_with_xbox")) {
            throw new BadRequestResponse("Error while acquiring Minecraft token! It's possible that Minecraft auth servers are down, please try again in a while!");
        }

        throw new BadRequestResponse("Minecraft link error D2 " + resp.statusCode() + ": " + json.toString(2));
    }

    @Nullable
    private static BaseMinecraftProfile checkMcProfile(String mcAccessToken) throws Exception {
        var request = HttpRequest.newBuilder(MC_PROFILE_URI)
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET().build();

        var resp = BotMain.HTTP_CLIENT.send(request, OAuthClient.jsonObject());
        var json = resp.body();

        if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
            if (!json.has("name") || !json.has("id")) {
                return null;
            }

            var name = json.getString("name");
            var uuid = json.getString("id");
            return new BaseMinecraftProfile(UUID.fromString(uuid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")), name);
        }

        if (json.getString("error").equals("NOT_FOUND")) {
            throw new BadRequestResponse("This Microsoft account isn't linked with a Minecraft profile!");
        }

        if (resp.statusCode() == 503) {
            throw new BadRequestResponse("Error while acquiring Minecraft profile! It's possible that Minecraft auth servers are down, please try again in a while!");
        }

        throw new BadRequestResponse("Minecraft link error E2 " + resp.statusCode() + ": " + json.toString(2));
    }

    private record BaseMinecraftProfile(UUID uuid, String name) {
    }
}
