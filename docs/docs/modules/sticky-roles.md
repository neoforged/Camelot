# Sticky Roles Module
The sticky roles module (id: `sticky-roles`, configuration class: `StickyRoles`) can be used to configure roles that
will "stick" to users when they leave the server, receiving them back when they join again.

In order to enable sticky roles in a guild, use the `/configuration sticky-roles` command. The bot will reply
with an ephemeral message with a button to enable sticky roles. Once pressed, sticky roles will be enabled for the guild and
configured in **blacklist** mode by default.

## Modes

Sticky roles function in one of 2 modes (the mode can be changed on a per-guild basis using the "Change mode" button in the configuration command):
- **blacklist** (default): all roles _but_ the ones selected will stick to the user
- **whitelist**: _only_ the roles selected will stick to the user

## Bot Permissions
The following bot permissions are required by this module: `Manage Roles`.  
For the bot to be able to give a user their sticky roles back, the bot must be situated above all roles you wish to stick.
