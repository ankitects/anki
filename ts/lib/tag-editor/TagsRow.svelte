<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import Col from "$lib/components/Col.svelte";
    import ConfigInput from "$lib/components/ConfigInput.svelte";
    import RevertButton from "$lib/components/RevertButton.svelte";
    import Row from "$lib/components/Row.svelte";
    import type { Breakpoint } from "$lib/components/types";

    import TagEditor from "./TagEditor.svelte";

    export let tags: string[];
    export let keyCombination: string | undefined = undefined;
    export let breakpoint: Breakpoint = "md";

    const tagsWritable = writable<string[]>(tags);
</script>

<Row --cols={13}>
    <Col --col-size={7} {breakpoint}>
        <slot />
    </Col>
    <Col --col-size={6} {breakpoint}>
        <ConfigInput>
            <TagEditor
                tags={tagsWritable}
                on:tagsupdate={({ detail }) => (tags = detail.tags)}
                {keyCombination}
            />
            <RevertButton slot="revert" bind:value={$tagsWritable} defaultValue={[]} />
        </ConfigInput>
    </Col>
</Row>
