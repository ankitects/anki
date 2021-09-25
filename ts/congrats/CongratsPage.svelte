<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Scheduler } from "lib/proto";
    import { buildNextLearnMsg } from "./lib";
    import { bridgeLink } from "lib/bridgecommand";

    export let info: Scheduler.CongratsInfoResponse;
    import * as tr from "lib/i18n";

    const congrats = tr.schedulingCongratulationsFinished();
    let nextLearnMsg: string;
    $: nextLearnMsg = buildNextLearnMsg(info);
    const today_reviews = tr.schedulingTodayReviewLimitReached();
    const today_new = tr.schedulingTodayNewLimitReached();

    const unburyThem = bridgeLink("unbury", tr.schedulingUnburyThem());
    const buriedMsg = tr.schedulingBuriedCardsFound({ unburyThem });
    const customStudy = bridgeLink("customStudy", tr.schedulingCustomStudy());
    const customStudyMsg = tr.schedulingHowToCustomStudy({
        customStudy,
    });
</script>

<div class="congrats-outer">
    <div class="congrats-inner">
        <h3>{congrats}</h3>

        <p>{nextLearnMsg}</p>

        {#if info.reviewRemaining}
            <p>{today_reviews}</p>
        {/if}

        {#if info.newRemaining}
            <p>{today_new}</p>
        {/if}

        {#if info.bridgeCommandsSupported}
            {#if info.haveSchedBuried || info.haveUserBuried}
                <p>
                    {@html buriedMsg}
                </p>
            {/if}

            {#if !info.isFilteredDeck}
                <p>
                    {@html customStudyMsg}
                </p>
            {/if}
        {/if}

        {#if info.deckDescription}
            <div class="description">
                {@html info.deckDescription}
            </div>
        {/if}
    </div>
</div>

<style>
    .congrats-outer {
        display: flex;
        justify-content: center;
    }

    .congrats-inner {
        max-width: 30em;
    }

    .description {
        border: 1px solid var(--border);
        padding: 1em;
    }
</style>
