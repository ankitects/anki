<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        CongratsInfoResponse,
        TimeBudgetCongrats,
    } from "@generated/anki/scheduler_pb";
    import { congratsInfo } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { bridgeLink } from "@tslib/bridgecommand";

    import Col from "$lib/components/Col.svelte";
    import Container from "$lib/components/Container.svelte";

    import { buildNextLearnMsg } from "./lib";
    import { onMount } from "svelte";

    export let info: CongratsInfoResponse;
    export let refreshPeriodically = true;

    const congrats = tr.schedulingCongratulationsFinished();
    const continueTimeBudgetMsg = tr.schedulingTimeBudgetContinue({
        continueReviewing: bridgeLink(
            "continueTimeBudget",
            tr.schedulingTimeBudgetContinueReviewing(),
        ),
    });
    // a deck studied with a time budget, before continuing past its goal
    let timeBudget: TimeBudgetCongrats | undefined;
    $: timeBudget = info.timeBudget?.continued === false ? info.timeBudget : undefined;
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

    onMount(() => {
        if (refreshPeriodically) {
            setInterval(async () => {
                try {
                    info = await congratsInfo({}, { alertOnError: false });
                } catch {
                    console.log("congrats fetch failed");
                }
            }, 60000);
        }
    });
</script>

<Container --gutter-block="1rem" --gutter-inline="2px" breakpoint="sm">
    <Col --col-justify="center">
        <div class="congrats">
            {#if timeBudget}
                <h1>
                    {timeBudget.timeUsedUp
                        ? tr.schedulingTimeBudgetGoalReached()
                        : tr.schedulingTimeBudgetNothingLeft()}
                </h1>

                {#if info.bridgeCommandsSupported}
                    <p>
                        {@html continueTimeBudgetMsg}
                    </p>
                {/if}
            {:else}
                <h1>{congrats}</h1>
            {/if}

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
    </Col>
</Container>

<style lang="scss">
    .congrats {
        margin-top: 2em;
        max-width: 30em;
        font-size: var(--font-size);

        :global(a) {
            color: var(--fg-link);
            text-decoration: none;
        }
    }

    .description {
        border: 1px solid var(--border);
        padding: 1em;
    }
</style>
