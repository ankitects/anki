<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { localizedNumber } from "../lib/i18n";
    import { pageTheme } from "../sveltelib/theme";

    export let value: number;
    export let min = 1;
    export let max = 9999;

    let stringValue: string;
    $: stringValue = localizedNumber(value, 2);

    function update(this: HTMLInputElement): void {
        value = Math.min(max, Math.max(min, parseFloat(this.value)));
    }
</script>

<input
    type="number"
    class="form-control"
    class:nightMode={$pageTheme.isDark}
    {min}
    {max}
    step="0.01"
    value={stringValue}
    on:blur={update}
/>

<style lang="scss">
    @use "sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }
</style>
