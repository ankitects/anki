<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<!-- Based on https://github.com/wobsoriano/svelte-portal; MIT -->

<script lang="ts">
    import { mount, unmount, type Snippet } from "svelte";
    import RenderChildren from "./RenderChildren.svelte";

    const {
        children,
        target,
    }: {
        children: Snippet;
        target: HTMLElement | null;
    } = $props();

    $effect(() => {
        let app: Record<string, unknown>;

        if (target) {
            app = mount(RenderChildren, {
                target: target,
                props: {
                    children,
                    $$slots: { default: children },
                },
            });
        }

        return () => {
            if (app) {
                unmount(app);
            }
        };
    });
</script>

{#if !target}
    <!-- eslint-disable -->
    <!-- svelte-ignore slot_element_deprecated -->
    <slot />
{/if}
