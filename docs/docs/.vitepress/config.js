export default {
    lang: 'en-US',

    title: 'Camelot',
    description: 'Documentation for the Camelot Discord bot',

    themeConfig: {
        logo: '/favicon.png',
        socialLinks: [
            { icon: 'github', link: 'https://github.com/neoforged/Camelot' }
        ],
        sidebar: [
            {
                text: 'Setting up Camelot',
                link: '/get-started'
            },
            {
                text: 'Formats',
                link: '/formats'
            },
            {
                text: 'Modules',
                link: '/modules/',
                collapsed: false,
                items: [
                    {
                        text: 'Counters',
                        link: '/modules/counters'
                    },
                    {
                        text: 'File Preview',
                        link: '/modules/file-preview'
                    },
                    {
                        text: 'Message Referencing',
                        link: '/modules/message-referencing'
                    },
                    {
                        text: 'Moderation',
                        link: '/modules/moderation'
                    },
                    {
                        text: 'Sticky Roles',
                        link: '/modules/sticky-roles'
                    },
                    {
                        text: 'Thread Pings',
                        link: '/modules/thread-pings'
                    }
                ]
            }
        ],
        nav: [{
            text: 'Setting up Camelot',
            link: '/get-started',
        }, {
            text: 'Modules',
            link: '/modules/',
            activeMatch: '/modules/'
        }],
        footer: {
            message: 'Released under the MIT License.',
            copyright: 'Copyright © 2024 NeoForged'
        }
    }
}
