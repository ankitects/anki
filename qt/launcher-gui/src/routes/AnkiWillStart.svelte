<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" module>
    let count = $state(3);
    let timeout: any = $state(undefined);
    let firstRun = $state(true);
    let launch = $state(Promise.resolve({}));
</script>

<script lang="ts">
    import { tr } from "./stores";
    import Icon from "$lib/components/Icon.svelte";
    import { checkDecagramOutline } from "$lib/components/icons";
    import Warning from "./Warning.svelte";
    import { onMount } from "svelte";
    import { exit, launchAnki } from "@generated/backend-launcher";
    import type { ChooseVersionResponse } from "@generated/anki/launcher_pb";
    import IconConstrain from "$lib/components/IconConstrain.svelte";
    import Spinner from "./Spinner.svelte";

    const { res }: { res: ChooseVersionResponse } = $props();
    const { warmingUp } = res;

    if (firstRun) {
        firstRun = false;
        launch = launchAnki({});

        const countdown = () => {
            count -= 1;
            if (count <= 0) {
                exit({});
            } else {
                timeout = setTimeout(countdown, 1000);
            }
        };

        if (!warmingUp) {
            timeout = setTimeout(countdown, 1000);
            onMount(() => {
                return () => clearTimeout(timeout);
            });
        } else {
            // wait for warm-up to end
            launch.then(countdown);
        }
    }
</script>

{#await launch}
    <Spinner>
        <div>{$tr.launcherAnkiIsWarmingUp()}</div>
        {#if warmingUp}
            <div class="m-1">{$tr.launcherThisMayTake()}</div>
        {/if}
    </Spinner>
{:then}
    <!-- TODO: replace with Spinner showing checkmark/cross -->
    <Warning warning={$tr.launcherWillCloseIn({ count })} className="alert-success">
        <IconConstrain iconSize={100} slot="icon">
            <Icon icon={checkDecagramOutline} />
        </IconConstrain>
    </Warning>
{/await}
