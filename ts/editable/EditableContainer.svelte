<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Editable from "./Editable.svelte";
    import { getContext, onDestroy } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { loadStyleLink, loadStyleTag } from "./style";

    export let content: string;
    export let focusOnMount: boolean = false;

    let shadow: ShadowRoot;

    export function addStyleLink(href: string): [HTMLLinkElement, Promise<void>] {
        return loadStyleLink(href, shadow);
    }

    export function addStyleTag(): [HTMLStyleElement, Promise<void>] {
        return loadStyleTag(shadow);
    }

    let rootStyle: HTMLLinkElement;
    let rootPromise: Promise<void>;

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

    const mutationObserver = new MutationObserver(console.log);

    function attachMutationObserver(element: Element) {
        mutationObserver.observe(element, { childList: true });
    }

    function attachShadow(element: Element) {
        shadow = element.attachShadow({ mode: "open" });
        [rootStyle, rootPromise] = addStyleLink("./_anki/css/editable-build.css");

        new Editable({
            target: shadow as any,
            props: { content, focusOnMount },
        });
    }

    const nightMode = getContext(nightModeKey);

    onDestroy(() => mutationObserver.disconnect());
</script>

<div
    use:attachMutationObserver
    use:attachShadow
    class="editable-container"
    class:night-mode={nightMode}
/>
