<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import IconConstrain from "../components/IconConstrain.svelte";
    import { altPressed, controlPressed, shiftPressed } from "../lib/keys";
    import { PaneArray } from "../lib/pane-array";
    import { Notetypes, notetypes } from "../lib/proto";
    import { randomUUID } from "../lib/uuid";
    import type { DrawerItemType } from "./drawer";
    import { Drawer } from "./drawer";
    import DrawerTitle from "./DrawerTitle.svelte";
    import FieldConfigEditor from "./FieldConfigEditor.svelte";
    import { fieldIcon } from "./icons";
    import InsertionPoint from "./InsertionPoint.svelte";
    import { Panes } from "./panes";

    export let notetypeId = 0;

    let fields: Notetypes.Notetype.Field[] = [];

    const latestPane = writable<string>(null as any);
    let panes = new PaneArray<Notetypes.Notetype.Field>();

    let drawerRoot: DrawerItemType<
        Notetypes.Notetype.Field[] | Notetypes.Notetype.Field
    >;

    async function init(notetypeId: number): Promise<void> {
        if (!notetypeId) {
            return;
        }

        const notetype = await notetypes.getNotetype(
            Notetypes.NotetypeId.create({ ntid: notetypeId }),
        );
        fields.push(...notetype.fields);

        const id = randomUUID();

        $latestPane = id;
        panes.push({
            id,
            data: notetype.fields[0],
        });

        const children = fields.map((value) => ({
            name: value.name,
            data: value,
            children: [],
        }));

        drawerRoot = {
            name: "/",
            data: fields,
            children,
        };

        fields = fields;
    }

    $: init(notetypeId);

    function splitVertical(data: Notetypes.Notetype.Field): void {
        const id = randomUUID();

        const newPane = {
            id,
            data,
        };

        panes.splitVertical($latestPane!, newPane);
        $latestPane = id;
    }

    function splitHorizontal(data: Notetypes.Notetype.Field): void {
        const id = randomUUID();

        const newPane = {
            id,
            data,
        };

        panes.splitHorizontal($latestPane!, newPane);
        $latestPane = id;
    }

    function onItemClick(event: any, index: number): void {
        const data = fields[index];

        if (controlPressed(event)) {
            if (altPressed(event)) {
                panes.makeOnly({
                    id: $latestPane,
                    data,
                });
            } else if (shiftPressed(event)) {
                splitHorizontal(data);
            } else {
                splitVertical(data);
            }
        } else {
            panes.replace($latestPane, data);
        }

        panes = panes;
    }

    let dragActive = false;

    function initializeDrag({ dataTransfer }: DragEvent, index: string): void {
        dragActive = true;
        dataTransfer!.setData("text/plain", index);
        dataTransfer!.effectAllowed = "linkMove";
    }

    function moveField(from: number, to: number): void {
        const spliced = drawerRoot.children.splice(from, 1);
        const toIndex = to > from ? to - 1 : to;

        drawerRoot.children.splice(toIndex, 0, ...spliced);
        drawerRoot = drawerRoot;
    }

    function paneHSplit({ detail: paneId }): void {
        splitHorizontal(panes.findData(paneId)!);
        panes = panes;
    }

    function paneVSplit({ detail: paneId }): void {
        splitVertical(panes.findData(paneId)!);
        panes = panes;
    }
</script>

<div class="fields-page">
    {#if drawerRoot}
        <Drawer root={drawerRoot} let:data>
            <svelte:fragment slot="before">
                {#if !Array.isArray(data) && data.ord.val === 0}
                    <InsertionPoint
                        slot="before"
                        active={dragActive}
                        on:insertion={({ detail: from }) => moveField(from, 0)}
                    />
                {/if}
            </svelte:fragment>

            {#if Array.isArray(data)}
                <DrawerTitle>
                    <IconConstrain iconSize={90}>
                        {@html fieldIcon}
                    </IconConstrain>
                    <span>Fields</span>
                </DrawerTitle>
            {:else}
                <DrawerTitle
                    on:click={(event) => onItemClick(event, data.ord.val)}
                    on:dragstart={(event) => initializeDrag(event, data.ord.val)}
                    on:dragend={() => (dragActive = false)}
                >
                    <IconConstrain iconSize={90}>
                        {@html fieldIcon}
                    </IconConstrain>
                    {data.name}
                </DrawerTitle>
            {/if}

            <svelte:fragment slot="after">
                {#if !Array.isArray(data)}
                    <InsertionPoint
                        active={dragActive}
                        on:insertion={({ detail: from }) =>
                            moveField(from, data.ord.val + 1)}
                    />
                {/if}
            </svelte:fragment>
        </Drawer>
    {/if}

    <Panes
        {panes}
        on:panefocus={({ detail: paneId }) => latestPane.set(paneId)}
        on:panehsplit={paneHSplit}
        on:panevsplit={paneVSplit}
        let:id
        let:data
    >
        <span class="field-header" class:active={id === $latestPane} slot="header">
            <IconConstrain iconSize={90} top={-2}>
                {@html fieldIcon}
            </IconConstrain>
            {data.name}
        </span>

        <FieldConfigEditor slot="content" config={data.config} />
    </Panes>
</div>

<style lang="scss">
    .fields-page {
        display: flex;
        height: 100%;
        width: 100%;
    }

    .field-header {
        &.active :global(svg) {
            fill: cornflowerblue;
        }
    }
</style>
