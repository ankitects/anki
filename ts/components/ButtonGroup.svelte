<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { DynamicSvelteComponent } from "../sveltelib/registration";
    import type { Identifier } from "../lib/children-access";

    export interface ButtonGroupAPI {
        insert(button: DynamicSvelteComponent, position?: Identifier): void;
        append(button: DynamicSvelteComponent, position?: Identifier): void;
        show(position: Identifier): void;
        hide(position: Identifier): void;
        toggle(position: Identifier): void;
    }
</script>

<script lang="ts">
    import ButtonGroupItem from "./ButtonGroupItem.svelte";
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import { buttonGroupKey } from "./context-keys";
    import type { ButtonRegistration } from "./buttons";
    import { ButtonPosition } from "./buttons";
    import dynamicMounting from "../sveltelib/registration";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let size: number | undefined = undefined;
    export let wrap: boolean | undefined = undefined;

    $: buttonSize = size ? `--buttons-size: ${size}rem; ` : "";
    let buttonWrap: string;
    $: if (wrap === undefined) {
        buttonWrap = "";
    } else {
        buttonWrap = wrap ? `--buttons-wrap: wrap; ` : `--buttons-wrap: nowrap; `;
    }

    $: style = buttonSize + buttonWrap;

    export let api: Partial<ButtonGroupAPI> | undefined = undefined;

    function makeRegistration(): ButtonRegistration {
        const detach = writable(false);
        const position = writable(ButtonPosition.Standalone);
        return { detach, position };
    }

    const { items, dynamicItems, registerComponent, createInterface, resolve } =
        dynamicMounting(makeRegistration);

    $: for (const [index, item] of $items.entries()) {
        item.position.update(() => {
            if ($items.length === 1) {
                return ButtonPosition.Standalone;
            } else if (index === 0) {
                return ButtonPosition.InlineStart;
            } else if (index === $items.length - 1) {
                return ButtonPosition.InlineEnd;
            } else {
                return ButtonPosition.Center;
            }
        });
    }

    setContext(buttonGroupKey, registerComponent);

    if (api) {
        Object.assign(api, createInterface() as ButtonGroupAPI);
    }
</script>

<div
    {id}
    class="button-group btn-group {className}"
    {style}
    dir="ltr"
    role="group"
    use:resolve
>
    <slot />
    {#each $dynamicItems as item (item[0].id)}
        <ButtonGroupItem id={item[0].id} registration={item[1]}>
            <svelte:component this={item[0].component} {...item[0].props} />
        </ButtonGroupItem>
    {/each}
</div>

<style lang="scss">
    .button-group {
        display: flex;
        flex-flow: row var(--buttons-wrap);
    }
</style>
