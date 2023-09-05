<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let value: number[];

    let stringValue: string;
    $: stringValue = value.map((v) => v.toFixed(4)).join(", ");

    function update(this: HTMLInputElement): void {
        value = this.value.split(", ").map((v) => Number(v));
    }
</script>

<textarea value={stringValue} on:blur={update} class="w-100" />
