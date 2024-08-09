export default {
    lang: 'en-US',

    title: 'Camelot',
    description: 'Documentation for the Camelot Discord bot',
    base: '/Camelot/',

    head: [
        ['link', { rel: 'icon', type: 'image/x-icon', href: '/Camelot/favicon.ico' }],
    ],

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
                text: 'Modules',
                link: '/modules/',
                collapsed: false,
                items: [
                    {
                        text: 'Moderation',
                        link: '/modules/moderation'
                    },
                    {
                        text: 'Counters',
                        link: '/modules/counters'
                    },
                    {
                        text: 'Message Referencing',
                        link: '/modules/message-referencing'
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
