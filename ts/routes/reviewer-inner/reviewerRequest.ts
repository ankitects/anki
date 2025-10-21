// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
interface HtmlMessage {
    type: "html";
    value: string;
    css?: string;
    bodyclass?: string;
}

export type InnerReviewerRequest = HtmlMessage;
