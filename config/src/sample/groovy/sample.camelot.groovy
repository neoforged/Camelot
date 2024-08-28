import net.neoforged.camelot.config.module.*

camelot {
    final secrets = loadProperties('secrets.properties')

    token = secret(env('BOT_TOKEN'))
    prefix = '!'

    module(Tricks) {
        prefixEnabled = true
        encouragePromotedTricks = true
        trickMasterRole = 456
    }

    module(FilePreview) {
        // auth = patAuthentication('ghp_')
    }

    module(InfoChannels) {
        auth = appAuthentication {
            appId = '123'
            privateKey = secret(readFile('yes.pem'))
            installation = organization('example')
        }
    }

    module(FilePreview) {
        auth = patAuthentication(secret(secrets.GIST_PAT))
    }

    module(WebServer) {
        enabled = true
        serverUrl = 'https://camelot.example.com'
    }

    module(BanAppeals) {
        enabled = true
        appealsChannel(guild: 1234, channel: 3234)

        mail {
            mailProperties = [
                    'smtp.auth': true,
                    'transport.protocol': 'smtp',
                    'smtp.host': 'smtp.gmail.com',
                    'smtp.port': '587',
                    'smtp.starttls.enable': true,
                    'smtp.starttls.required': true
            ]

            username = 'example@gmail.com'
            password = secret(env('MAIL_PASSWORD'))

            sendAs = 'example@example.net'
        }

        discordAuth {
            clientId = 'yes'
            clientSecret = '....'
        }
    }

    module(MinecraftVerification) {
        enabled = true

        discordAuth { }
        microsoftAuth { }
    }
}
