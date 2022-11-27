// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export * from "../../../out/ts/lib/i18n/modules";
export * from "./bundles";
export * from "./utils";
// this is an ugly hack to inject code into a generated module
import { funcs } from "../../../out/ts/lib/ftl";
import { getMessage } from "./bundles";

funcs.getMessage = getMessage;
