<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";
    import { stepsToString, stringToSteps } from "./steps";

    export let value: number[];

    let stringValue: string;
    $: stringValue = stepsToString(value);

    function update(this: HTMLInputElement): void {
        value = stringToSteps(this.value);
    }
</script>

<input
    type="text"
    value={stringValue}
    class="form-control"
    class:nightMode={$pageTheme.isDark}
    on:blur={update}
/>

<style lang="scss">
    @use "sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }
</style>
