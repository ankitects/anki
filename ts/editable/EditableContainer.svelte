<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Editable from "./Editable.svelte";
    import CustomStyles from "./CustomStyles.svelte";

    import { promiseResolve } from "lib/promise";
    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import type { DecoratedElementConstructor } from "./decorated";
    import { getContext, getAllContexts, createEventDispatcher } from "svelte";
    import type { Readable } from "svelte/store";
    import { nightModeKey } from "components/context-keys";

    import { fontFamilyKey, fontSizeKey, directionKey } from "lib/context-keys";

    const [editablePromise, editableResolve] = promiseResolve<Editable>();
    export { editablePromise };

    const [customStylesPromise, customStylesResolve] = promiseResolve<CustomStyles>();

    const [userBaseStyle, userBaseResolve] = promiseResolve<StyleObject>();
    const [userBaseRule, userBaseRuleResolve] = promiseResolve<CSSStyleRule>();

    export let color: string;
    export let decoratedComponents: DecoratedElementConstructor[];

    const fontFamily = getContext<Readable<string>>(fontFamilyKey);
    const fontSize = getContext<Readable<number>>(fontSizeKey);
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    $: userBaseRule.then((rule: CSSStyleRule) => (rule.style.color = color));
    $: userBaseRule.then((rule: CSSStyleRule) => (rule.style.fontFamily = $fontFamily));
    $: userBaseRule.then(
        (rule: CSSStyleRule) => (rule.style.fontSize = `${$fontSize}px`)
    );
    $: userBaseRule.then((rule: CSSStyleRule) => (rule.style.direction = $direction));

    /* isRightToLeft(): boolean { */
    /*     return this.baseRule!.style.direction === "rtl"; */
    /* } */

    const allContexts = getAllContexts();
    const dispatch = createEventDispatcher();

    let overlayRelative: HTMLElement;
    let shadow: ShadowRoot;

    function focusChangeWithinContainer(event: FocusEvent): boolean {
        return overlayRelative!.contains(event.relatedTarget as Node);
    }

    const ifEventOutsideContainer =
        (eventName: string) =>
        (event: FocusEvent): void => {
            if (!focusChangeWithinContainer(event)) {
                dispatch(eventName);
            }
        };

    function attachShadow(element: Element) {
        shadow = element.attachShadow({ mode: "open" });

        const styles = [
            {
                id: "rootStyle",
                type: "link" as "link",
                href: "./_anki/css/editable-build.css",
            } as StyleLinkType,
        ];

        const customStyles = new CustomStyles({
            target: shadow as any,
            props: { styles },
        });

        customStyles.addStyleTag("userBase").then(userBaseResolve);

        userBaseStyle.then((baseStyle: StyleObject) => {
            const sheet = baseStyle.element.sheet as CSSStyleSheet;
            const baseIndex = sheet.insertRule("anki-editable {}");
            userBaseRuleResolve(sheet.cssRules[baseIndex] as CSSStyleRule);
        });

        customStylesResolve(customStyles);

        const editable = new Editable({
            target: shadow as any,
            props: { decoratedComponents },
            context: allContexts,
        } as any);

        editable.$on("focus", ifEventOutsideContainer("editablefocus"));
        editable.$on("input", () => dispatch("editableinput"));
        editable.$on("custominput", () => dispatch("editableinput"));
        editable.$on("blur", ifEventOutsideContainer("editableblur"));

        editableResolve(editable);
    }

    const nightMode = getContext(nightModeKey);
</script>

<div
    bind:this={overlayRelative}
    class="overlay-relative"
    on:blur={ifEventOutsideContainer("editableblur")}
>
    <div use:attachShadow class="editable-container" class:night-mode={nightMode} />

    <!-- slot for overlays -->
    {#await customStylesPromise then customStyles}
        {#await editablePromise then editableComponent}
            {#await editableComponent.editablePromise then editable}
                <slot {editable} {customStyles} />
            {/await}
        {/await}
    {/await}
</div>

<style lang="scss">
    .overlay-relative {
        position: relative;
    }
</style>
