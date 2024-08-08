# Moderation Module
The moderation module (id: `moderation`, configuration class: `Moderation`) contains the following moderation commands:
- `/ban`: ban a member; an optional ban duration can be specified, otherwise the ban is permanent
- `/unban`: unban a member
- `/kick`: kick a member
- `/mute`: mute a member using a timeout; the maximum mute duration is 28 days
- `/unmute`: unmute a member
- `/warn add`: give a member a warning
- `/warn delete`: delete a warning by ID; the ID of a warning is visible in the mod logs
- `/note add`: add a note to a member; notes are only visible to moderators and not to the noted user
- `/note remove`: remove a note by ID; the ID of a note is visible in the mod logs
- `/purge`: delete a number of messages from the channel you run the command in; you may optionally filter by user
- `/modlogs`: view the moderation actions taken against an user; you may include an action type or exclude one from the list; when ran by moderators, this command can be used to view the mod logs of any user; when ran by other members, they can only view their own mod logs, excluding notes

## Bot Permissions
The following bot permissions are required by this module: `Kick Members`, `Ban Members`, `Time out Members`, `Manage Messages`.

## User Permissions
The ban, kick, mute and purge (message deletion) commands require their respective Discord permissions to use.  
All other moderation commands require the `Time out Members` permission.
A moderator cannot attempt to moderate a person with a role higher than them, nor can they moderate a person with a role higher than the bot's.
