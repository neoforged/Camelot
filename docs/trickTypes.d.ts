export interface Snowflake {
    id: string;

    getCreationDate(): Date;
}

export interface Mentionable extends Snowflake {
    asMention(): string;
}

export interface User extends Mentionable {
    name: string;
    avatarUrl: string;

    /**
     * @deprecated Discord is replacing discriminators with unique usernames
     */
    discriminator: string;

    /**
     * @deprecated Discord is replacing discriminators with unique usernames
     */
    asTag(): string;
}

export interface Member extends Mentionable {
    user: User;

    avatarUrl: string | null;
    effectiveAvatarUrl: string;

    nickname: string | null;
    effectiveName: string;

    color: number;

    getJoinTime(): Date;
    getPermissions(): string[]; // TODO - replace with an enum
    getRoles(): Role[];
}

export interface Role extends Mentionable {
    name: string;
    color: number;

    getGuild(): Guild;
}

export interface Emoji extends Mentionable {
    name: string;
}

export interface Guild extends Snowflake {
    name: string;
    memberCount: number;
    iconUrl: string;

    getRoles(): Role[];
    getEmojis(): Emoji[];

    getCounter(counter: string): number | null;
}

export enum ChannelType {
    TEXT
}
export interface Channel extends Mentionable {
    name: string;
    type: ChannelType;
}

export interface JDA {
    getUserById(id: string): User | null;
    getEmojis(): Emoji[];
}

export declare namespace Options {
    export enum Type {
        string, member, user, int, boolean
    }

    export type OptionConfig = {
        description: string;
        name?: string | null;
        required?: boolean | null;
        hidden?: boolean | null;
        type: Type | any;
        default?: any | null;
    }

    export interface Builder extends Array<any> {
        optNamed(name: ListOrOne<string>, config: OptionConfig): Builder;
        optPositional(index: number, config: OptionConfig): Builder;
        parse(): any[];
    }
}

export const user: User;
export const member: Member;
export const guild: Guild;
export const channel: Channel;
export const options: Options.Builder;

export function reply(text: string);
export function replyEmbed(embed: ListOrOne<Embeds.Embed>);

export type ListOrOne<T> = T | T[];
export function catchUndefined<T>(f: () => T): T | null;


/**
 * If a trick is privileged, this property gives it access to less 'safe' operations.
 */
export const privileged: Privileged | null;

export interface Privileged {
    httpGetJson(url: string): string
}

declare namespace Embeds {
    export type Title = {
        value: string | null;
        url: string | null;
    }

    export type Footer = {
        value: string | null,
        iconUrl: string | null
    }

    export type Field = {
        name: string;
        value: string;
        inline: boolean | null;
    }

    export type Embed = {
        title?: string | Title | null;
        description?: string | null;
        image?: string | null;
        fields?: ListOrOne<Field> | null;
        color?: number | null;
        footer?: string | Footer | null
    }
}
