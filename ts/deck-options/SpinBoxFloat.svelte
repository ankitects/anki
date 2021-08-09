<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let value: number;
    export let min = 1;
    export let max = 9999;

    let stringValue: string;
    $: stringValue = value.toFixed(2);

    const nightMode = getContext<boolean>(nightModeKey);

    function update(this: HTMLInputElement): void {
        value = Math.min(max, Math.max(min, parseFloat(this.value)));
    }
</script>

<input
    type="number"
    class="form-control"
    class:nightMode
    {min}
    {max}
    step="0.01"
    value={stringValue}
    on:blur={update}
/>

<style lang="scss">
    @use "ts/sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }
</style>
