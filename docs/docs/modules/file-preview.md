# File Preview Module
The file preview module (id: `file-preview`, configuration class: `FilePreview`) makes the bot react to
messages containing files or code blocks (that are either 10 or more lines in length or have more than 300 characters)
with a `gist` emoji. When the emoji is reacted with by another user, the bot will upload the files / code blocks contained in the message
to a [GitHub Gist](https://gist.github.com/) so that they're easier to read on both mobile and PC.

## Configuration
In order for this module to work, the `auth` must be set using a GitHub Personal Access Token with the `gist` permission:
```groovy{2-4}
camelot {
  module(FilePreview) {
    auth = patAuthentication('<insert GitHub PAT here>')
  }
}
```

### File extension whitelist
Not all files may be previewed. Camelot uses a configurable whitelist to decide which files may have a Gist created.  
You can append your own extensions like so:
```groovy{2-5}
camelot {
  module(FilePreview) {
    // Whitelist the abcd extension
    extensions += ['abcd']
  }
}
```

## Bot Permissions
The following bot permissions are required by this module: `Send Messages`, `Add Reactions`.
