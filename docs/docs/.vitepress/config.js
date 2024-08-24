export default {
    lang: 'en-US',

    title: 'Camelot',
    description: 'Documentation for the Camelot Discord bot',

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
