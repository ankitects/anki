<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import ConfigEditor from "./ConfigEditor.svelte";
    import type { DeckOptionsState } from "./lib";
    import { onMount, onDestroy } from "svelte";
    import { registerShortcut } from "lib/shortcuts";
    import type { Writable } from "svelte/store";
    import HtmlAddon from "./HtmlAddon.svelte";
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

    export let state: DeckOptionsState;
    let addons = state.addonComponents;

    export function auxData(): Writable<Record<string, unknown>> {
        return state.currentAuxData;
    }

    export function addSvelteAddon(component: DynamicSvelteComponent): void {
        $addons = [...$addons, component];
    }

    export function addHtmlAddon(html: string, mounted: () => void): void {
        $addons = [
            ...$addons,
            {
                component: HtmlAddon,
                html,
                mounted,
            },
        ];
    }

    let registerCleanup: () => void;
    onMount(() => {
        registerCleanup = registerShortcut(() => state.save(false), [
            ["Control", "Enter"],
        ]);
    });
    onDestroy(() => registerCleanup?.());
</script>

<style lang="scss">
    .editor {
        // without this, the initial viewport can be wrong
        overflow-x: hidden;
    }
</style>

<ConfigSelector {state} />

<div>
    <div id="modal">
        <!-- filled in later-->
    </div>

    <div class="editor">
        <ConfigEditor {state} />
    </div>
</div>
