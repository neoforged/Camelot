import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress/cli'
import { viteBundler } from '@vuepress/bundler-vite'

export default defineUserConfig({
  lang: 'en-US',

  title: 'Camelot',
  description: 'Documentation for the Camelot Discord bot',
  base: '/camelot/',

  theme: defaultTheme({
    logo: 'images/hero.png',
    sidebar: [
      {
        text: 'Setting up Camelot',
        link: '/get-started'
      },
      {
        text: 'Modules',
        link: '/modules/',
        collapsible: false,
        children: [
          {
            text: 'Moderation',
            link: '/modules/moderation'
          }
        ]
      }
    ],
    navbar: ['/', {
      text: 'Setting up Camelot',
      link: '/get-started',
    }, {
      text: 'Modules',
      link: '/modules/'
    }],
    docsDir: 'docs'
  }),

  bundler: viteBundler(),

})
