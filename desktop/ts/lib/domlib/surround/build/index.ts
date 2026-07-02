// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../tree";
import { buildFromNode } from "./build-tree";
import { extendAndMerge } from "./extend-merge";
import type { BuildFormat } from "./format";

/**
 * Builds a TreeNode forest structure from an input node.
 *
 * @remarks
 * This will remove matching elements from the DOM. This is necessary to make
 * some normalizations.
 *
 * @param node: This node should have no matching ancestors.
 */
export function build<T>(node: Node, build: BuildFormat<T>): TreeNode[] {
    return extendAndMerge(buildFromNode(node, build, []), build);
}

export { BuildFormat, ReformatBuildFormat, UnsurroundBuildFormat } from "./format";
