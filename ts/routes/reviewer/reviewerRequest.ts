// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
interface AudioMessage {
    type: "audio";
    answerSide: boolean;
    index: number;
}

interface UpdateTypedAnswerMessage {
    type: "typed";
    value: string;
}

interface KeyPressMessage {
    type: "keypress";
    key: string;
}

export type ReviewerRequest = AudioMessage | UpdateTypedAnswerMessage | KeyPressMessage;
