<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    interface Identifiable {
        id: string;
    }

    export interface StyleLinkType extends Identifiable {
        type: "link";
        href: string;
    }

    export interface StyleTagType extends Identifiable {
        type: "style";
    }

    export type StyleType = StyleLinkType | StyleTagType;

    type StyleHTMLTag = HTMLStyleElement | HTMLLinkElement;

    export interface StyleObject {
        element: StyleHTMLTag;
    }

    interface CustomStylesContext {
        register: (id: string, object: StyleObject) => void;
        deregister: (id: string) => void;
    }

    export function getCustomStylesContext(): CustomStylesContext {
        return getContext(customStylesKey);
    }

    export const customStylesKey = Symbol("customStyles");
</script>

<script lang="ts">
    import { getContext, setContext } from "svelte";

    import StyleLink from "./StyleLink.svelte";
    import StyleTag from "./StyleTag.svelte";

    export let styles: StyleType[];
    export const styleMap = new Map<string, StyleObject>();

    const resolvers = new Map<string, (object: StyleObject) => void>();

    function register(id: string, object: StyleObject): void {
        styleMap.set(id, object);

        if (resolvers.has(id)) {
            resolvers.get(id)!(object);
            resolvers.delete(id);
        }
    }

    function deregister(id: string): void {
        styleMap.delete(id);
    }

    setContext(customStylesKey, { register, deregister });

    function waitForRegistration(id: string): Promise<StyleObject> {
        let styleResolve: (element: StyleObject) => void;
        const promise = new Promise<StyleObject>((resolve) => (styleResolve = resolve));

        resolvers.set(id, styleResolve!);
        return promise;
    }

    export function addStyleLink(id: string, href: string): Promise<StyleObject> {
        styles.push({ id, type: "link", href });
        styles = styles;

        return waitForRegistration(id);
    }

    export function addStyleTag(id: string): Promise<StyleObject> {
        styles.push({ id, type: "style" });
        styles = styles;

        return waitForRegistration(id);
    }
</script>

{#each styles as style (style.id)}
    {#if style.type === "link"}
        <StyleLink id={style.id} href={style.href} />
    {:else}
        <StyleTag id={style.id} />
    {/if}
{/each}
