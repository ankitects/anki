// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { PreferenceRaw, PreferencePayload } from "sveltelib/preferences";
import pb from "anki/backend_proto";
import { postRequest } from "anki/postrequest";
import { getPreferences } from "sveltelib/preferences";

async function getEditorData(): Promise<pb.BackendProto.EditorOut> {
    return pb.BackendProto.EditorOut.decode(
        await postRequest("/_anki/editorData", JSON.stringify({}))
    );
}

async function getEditorPreferences(): Promise<pb.BackendProto.EditorPreferences> {
    return pb.BackendProto.EditorPreferences.decode(
        await postRequest("/_anki/editorPreferences", JSON.stringify({}))
    );
}

async function setEditorPreferences(
    prefs: PreferencePayload<pb.BackendProto.EditorPreferences>
): Promise<void> {
    await postRequest(
        "/_anki/setEditorPreferences",
        pb.BackendProto.EditorPreferences.encode(prefs).finish()
    );
}

export const preferences = getPreferences(
    getEditorPreferences,
    setEditorPreferences,
    pb.BackendProto.EditorPreferences.toObject.bind(
        pb.BackendProto.EditorPreferences
    ) as (
        preferences: pb.BackendProto.EditorPreferences,
        options: { defaults: boolean }
    ) => PreferenceRaw<pb.BackendProto.EditorPreferences>
);
export const data = getEditorData();
