# Thread Pings Module
The thread pings module (id: `thread-pings`, configuration class: `ThreadPings`) allows for certain roles to be 
automatically added to public threads created in specific channels, categories, or the server (a.k.a. the guild). The
automatic addition is done in a way that doesn't send push notifications to the added users.

The server, each category, and each channel can be configured with a list of roles to be added to public threads created
within their scope. For categories and channels, roles can be configured to be exempted from being automatically added.

The module provide three configuration commands:
- The `/configuration thread-pings configure-guild` command provides an interface to configure thread pings for the 
  entire guild/server.
- The `/configuration thread-pings configure-channel` command provides an interface to configure thread pings for a 
  specific channel, which may be a channel category.
- The `/configuration thread-pings view` command, when given a channel, shows the current setup of roles for the channel, 
  its parent category (if available), and the guild, including which roles will be automatically added and which are 
  exempted.

## Role Exemptions
In certain situations, it would be useful to exempt previously-configured roles from being automatically added in 
specific areas of the server. (For example, a highly active forum may want to not have all moderators always added, to 
avoid flooding their channel list with many threads.)

Roles can be exempted in categories and channels, which block those roles from being automatically added to a thread even
if configured at a different level. (In the previous example: moderators may be configured to be added server-wide, but
the forum channel can exempt the moderator role.)

All role exemptions are **unconditionally applied after** all configured ping roles are considered. This means, for 
example, that an exemption at the category level will still apply to a role even if it is explicitly configured for a
channel within that category.

## Bot Permissions
The following bot permissions are required by this module: `View Channels`, `Send Messages`, `Send Messages in Threads` 
and `Mention Everyone`.

The bot may function without the `Mention Everyone` permission, but roles which are not globally mentionable/pingable may
not be added to threads properly.
