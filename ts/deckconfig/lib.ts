// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import pb from "anki/backend_proto";
import { postRequest } from "anki/postrequest";

export async function getDeckConfigInfo(
    deckId: number
): Promise<pb.BackendProto.DeckConfigForUpdate> {
    return pb.BackendProto.DeckConfigForUpdate.decode(
        await postRequest("/_anki/deckConfigForUpdate", JSON.stringify({ deckId }))
    );
}

export type DeckConfigId = number;

export interface ConfigWithCount {
    config: pb.BackendProto.DeckConfig;
    useCount: number;
}

export interface DeckConfigState {
    deckName: string;
    selectedConfigId: DeckConfigId;
    removedConfigs: DeckConfigId[];
    renamedConfigs: Map<DeckConfigId, string>;
    allConfigs: ConfigWithCount[];
    defaults: pb.BackendProto.DeckConfig.Config;
}

export function stateFromUpdateData(
    data: pb.BackendProto.DeckConfigForUpdate
): DeckConfigState {
    const current = data.currentDeck as pb.BackendProto.DeckConfigForUpdate.CurrentDeck;
    return {
        deckName: current.name,
        selectedConfigId: current.configId,
        removedConfigs: [],
        renamedConfigs: new Map(),
        allConfigs: data.allConfig.map((config) => {
            return {
                config: config.config as pb.BackendProto.DeckConfig,
                useCount: config.useCount!,
            };
        }),
        defaults: data.defaults!.config! as pb.BackendProto.DeckConfig.Config,
    };
}
