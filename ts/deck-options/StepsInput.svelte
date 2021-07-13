<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { stepsToString, stringToSteps } from "./steps";

    export let value: number[];

    let stringValue: string;
    $: stringValue = stepsToString(value);

    const nightMode = getContext<boolean>(nightModeKey);

    function update(this: HTMLInputElement): void {
        value = stringToSteps(this.value);
    }
</script>

<input
    type="text"
    value={stringValue}
    class="form-control"
    class:nightMode
    on:blur={update}
/>

<style lang="scss">
    @use "ts/sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }
</style>
