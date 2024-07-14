<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import Item from "$lib/components/Item.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";

    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const settings = {
        onlyCountSelf: {
            title: tr.deckConfigLoadBalancerOnlyCountSelf(),
            help: tr.deckConfigLoadBalancerOnlyCountSelfTooltip(),
        },
        hideFromOthers: {
            title: tr.deckConfigLoadBalancerHideFromOthers(),
            help: tr.deckConfigLoadBalancerHideFromOthersTooltip(),
        },
    };
    const helpSections = Object.values(settings) as HelpItem[];

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.deckConfigLoadBalancerTitle()}>
    <HelpModal
        title={tr.deckConfigLoadBalancerTitle()}
        url={HelpPage.DeckOptions.loadBalancer}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SwitchRow
                bind:value={$config.loadBalancerOnlyCountSelf}
                defaultValue={defaults.loadBalancerOnlyCountSelf}
            >
                <SettingTitle
                    on:click={() => {
                        openHelpModal(Object.keys(settings).indexOf("onlyCountSelf"));
                    }}
                >
                    {settings.onlyCountSelf.title}
                </SettingTitle>
            </SwitchRow>
        </Item>
        <Item>
            <SwitchRow
                bind:value={$config.loadBalancerHideFromOthers}
                defaultValue={defaults.loadBalancerHideFromOthers}
            >
                <SettingTitle
                    on:click={() => {
                        openHelpModal(Object.keys(settings).indexOf("hideFromOthers"));
                    }}
                >
                    {settings.hideFromOthers.title}
                </SettingTitle>
            </SwitchRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
