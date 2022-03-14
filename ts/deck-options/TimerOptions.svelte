<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import SwitchRow from "./SwitchRow.svelte";
    import TitledContainer from "./TitledContainer.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    $: maximumAnswerSecondsAboveRecommended =
        $config.capAnswerTimeToSecs > 600
            ? tr.deckConfigMaximumAnswerSecsAboveRecommended()
            : "";
</script>

<TitledContainer title={tr.deckConfigTimerTitle()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SpinBoxRow
                bind:value={$config.capAnswerTimeToSecs}
                defaultValue={defaults.capAnswerTimeToSecs}
                min={30}
                max={7200}
                markdownTooltip={tr.deckConfigMaximumAnswerSecsTooltip()}
            >
                {tr.deckConfigMaximumAnswerSecs()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={maximumAnswerSecondsAboveRecommended} />
        </Item>

        <Item>
            <!-- AnkiMobile hides this -->
            <div class="show-timer-switch" style="display: contents;">
                <SwitchRow
                    bind:value={$config.showTimer}
                    defaultValue={defaults.showTimer}
                    markdownTooltip={tr.deckConfigShowAnswerTimerTooltip()}
                >
                    {tr.schedulingShowAnswerTimer()}
                </SwitchRow>
            </div>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
