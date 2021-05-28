<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let value: number;
    export let min = 1;
    export let max = 9999;

    let stringValue: string;
    $: stringValue = value.toFixed(2);

    function update(this: HTMLInputElement): void {
        value = Math.min(max, Math.max(min, parseFloat(this.value)));
    }
</script>

<input
    type="number"
    class="form-control"
    {min}
    {max}
    step="0.01"
    value={stringValue}
    on:blur={update}
/>
