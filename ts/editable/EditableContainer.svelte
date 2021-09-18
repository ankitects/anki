<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Editable from "./Editable.svelte";
    import { onMount, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { loadStyleLink, loadStyleTag } from "./style";

    let editableContainer: HTMLDivElement;
    let shadow: ShadowRoot;

    export function addStyleLink(href: string): [HTMLLinkElement, Promise<void>] {
        return loadStyleLink(shadow, href);
    }

    export function addStyleTag(): [HTMLStyleElement, Promise<void>] {
        return loadStyleTag(shadow);
    }

    /* const rootPromise = loadStyleLink(shadow, "./_anki/css/editable-build.css"); */
    /* const [baseStyle, basePromise] = loadStyleTag(shadow); */
    /* const [imageStyle, imagePromise] = loadStyleTag(shadow); */

    /* connectedCallback(): void { */
    /*     const sheet = this.baseStyle.sheet as CSSStyleSheet; */
    /*     const baseIndex = sheet.insertRule("anki-editable {}"); */
    /*     this.baseRule = sheet.cssRules[baseIndex] as CSSStyleRule; */
    /* } */

    /* initialize(color: string): void { */
    /*     this.setBaseColor(color); */
    /* } */

    /* setBaseColor(color: string): void { */
    /*     if (this.baseRule) { */
    /*         this.baseRule.style.color = color; */
    /*     } */
    /* } */

    /* setBaseStyling(fontFamily: string, fontSize: string, direction: string): void { */
    /*     if (this.baseRule) { */
    /*         this.baseRule.style.fontFamily = fontFamily; */
    /*         this.baseRule.style.fontSize = fontSize; */
    /*         this.baseRule.style.direction = direction; */
    /*     } */
    /* } */

    /* isRightToLeft(): boolean { */
    /*     return this.baseRule!.style.direction === "rtl"; */
    /* } */

    onMount(() => {
        shadow = editableContainer.attachShadow({ mode: "open" });
        new Editable({ target: shadow as any });
    });

    const nightMode = getContext(nightModeKey);
</script>

<div bind:this={editableContainer} class:night-mode={nightMode} />
