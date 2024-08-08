# Setting up Camelot

The recommended way of setting up Camelot is through Docker.

Camelot is available as an image on GHCR, at [`ghcr.io/neoforged/camelot`](https://github.com/neoforged/Camelot/pkgs/container/camelot).

Camelot stores its files in the `/home/camelot/` directory:
- `.db` SQLite databases in `/home/camelot/data`
- logs in `/home/camelot/bot_logs`
- static web server files in `/home/camelot/static` (only relevant if the web server module is enabled, as required by ban appeals or Minecraft ownership verification commands)

It is recommended that you mount the entire `/home/camelot` directory as a volume.
You **must** mount at least `/home/camelot/data` or you **will** experience data loss.

Camelot is also available as a `.jar` executable under [`net.neoforged.camelot:camelot`](https://central.sonatype.com/artifact/net.neoforged.camelot/camelot/overview) on Maven Central. The jar with the `-all` classifier contains all dependencies needed to run Camelot. Camelot runs on Java 21, and requires preview feature (enabled through `--enable-preview`).

## Configuration
Camelot is very configurable, using a [Groovy script](https://www.groovy-lang.org/) as its configuration format of choice.

By default, the configuration script is loaded from `/home/camelot/camelot.groovy`. This is customizable through the `--config` CLI argument.

### DSL
The DSL of the configuration script is available as an artifact on Maven Central, under [`net.neoforged.camelot:camelot-config`](https://central.sonatype.com/artifact/net.neoforged.camelot/camelot-config/overview). It is recommended that you configure the file with an IDE (like IntelliJ) and with the DSL on the classpath (you may use the IntelliJ module configuration to quickly add the dependency without Gradle or Maven).

It is also recommended that you import the `net.neoforged.camelot.config.module.*` package for proper IDE support.

The DSL is composed of a root-level `camelot` object that may be configured through a closure:
```groovy
camelot {
  // There are a few properties configured at this level
  token = 'abcd' // The Discord API bot token
  prefix = '!' // The command prefix
}
```

### Secrets and sensitive values
Some configuration options may be sensitive, such as the bot token. If you'd like to configure those outside the file, you may use an environment variable and pull it through `env(String)`.  
Additionally, any value that is sensitive can be wrapped by `secret(String)`. Doing so will provide a few protections against accidental leaks in the future.
```groovy
camelot {
  // Use the BOT_TOKEN environment variable for the Discord bot token
  token = secret(env('BOT_TOKEN'))
}
```

#### Configuring Modules
Each [module](./modules/) can be configured through the `module(Class<ModuleConfiguration>, Closure)` method of the `camelot` DSL. The available modules, their classes and configurable properties can be found on the [javadoc website of the DSL](https://javadoc.io/doc/net.neoforged.camelot/camelot-config/latest/index.html).

You may enable or disable a module by setting the `enabled` property that can be found on every module class:
```groovy{3-5}
camelot {
  // Disable the message referencing module
  module(MessageReferencing) {
    enabled = false
  }
}
```

::: tip
You may disable all modules by default as follows:
```groovy{2}:no-line-numbers
camelot {
  eachModule { enabled = false }
}
```
:::

## Permissions
The permissions required by this bot depend on the modules you plan on using. Consult each module's page to see what permissions it requires.

## Intents
The bot requires the following intents:
- `Guild Messages`
- `Message content`
- `Guild Emojis and Stickers`
- `Guild Message Reactions`
- `Guild Members`
- `Direct Messages`
- `Guild Moderation`
