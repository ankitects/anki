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

    let editable: Editable;

    const [editablePromise, editableResolve] = promiseResolve<Editable>();
    export { editablePromise };

    let customStyles: CustomStyles;

    const [userBaseStyle, userBaseResolve] = promiseResolve<StyleObject>();
    const [userBaseRule, userBaseRuleResolve] = promiseResolve<CSSStyleRule>();
    const [imageOverlayStyle, imageOverlayResolve] = promiseResolve<StyleObject>();

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

    let shadow: ShadowRoot;

    function attachShadow(element: Element) {
        shadow = element.attachShadow({ mode: "open" });

        const styles = [
            {
                id: "rootStyle",
                type: "link" as "link",
                href: "./_anki/css/editable-build.css",
            } as StyleLinkType,
        ];

        customStyles = new CustomStyles({
            target: shadow as any,
            props: { styles },
        });

        customStyles.addStyleTag("userBase").then(userBaseResolve);

        userBaseStyle.then((baseStyle: StyleObject) => {
            const sheet = baseStyle.element.sheet as CSSStyleSheet;
            const baseIndex = sheet.insertRule("anki-editable {}");
            userBaseRuleResolve(sheet.cssRules[baseIndex] as CSSStyleRule);
        });

        customStyles.addStyleTag("imageOverlay").then(imageOverlayResolve);

        editable = new Editable({
            target: shadow as any,
            props: { decoratedComponents },
            context: allContexts,
        } as any);

        editable.$on("editablefocus", () => dispatch("editablefocus"));
        editable.$on("editableinput", () => dispatch("editableinput"));
        editable.$on("editableblur", () => dispatch("editableblur"));

        editableResolve(editable);
    }

    const nightMode = getContext(nightModeKey);
</script>

<div class="overlay-relative">
    <div use:attachShadow class="editable-container" class:night-mode={nightMode} />

    <!-- slot for overlays -->
    {#await editablePromise then editableComponent}
        {#await editableComponent.editablePromise then editableDiv}
            <slot
                editable={editableDiv}
                imageOverlaySheet={imageOverlayStyle.then(
                    (object) => object.element.sheet
                )}
            />
        {/await}
    {/await}
</div>

<style lang="scss">
    .overlay-relative {
        position: relative;
    }
</style>
