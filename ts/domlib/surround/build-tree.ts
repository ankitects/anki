// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";
import { appendNode } from "./append-merge";
import { ChildNodeRange } from "./child-node-range";
import type { TreeNode } from "./formatting-tree";
import { BlockNode, FormattingNode, MatchNode } from "./formatting-tree";
import { elementIsNegligible, textIsNegligible } from "./node-negligible";
import type { ParseFormat } from "./parse-format";

function buildFromElement(
    element: Element,
    format: ParseFormat,
    covered: boolean,
): TreeNode | null {
    const match = format.matches(element);
    const covers = covered || Boolean(match.type);

    let children: TreeNode[] = [];
    for (const child of element.childNodes) {
        const node = buildFromNode(child, format, covers);

        if (node) {
            children = appendNode(children, node, format);
        }
    }

    const matchNode = MatchNode.make(
        element,
        match,
        covers || children.every((node: TreeNode): boolean => node.covered),
        children.every((node: TreeNode): boolean => node.insideRange),
    );

    if (children.length === 0 && !match.type) {
        return null;
    }

    if (children.length === 1) {
        const [only] = children;

        if (
            // blocking
            only instanceof BlockNode ||
            // ascension
            (only instanceof FormattingNode && format.tryAscend(only, matchNode))
        ) {
            return only;
        }
    }

    matchNode.replaceChildren(children);
    return matchNode;
}

function buildFromText(
    text: Text,
    format: ParseFormat,
    covered: boolean,
): FormattingNode | BlockNode {
    const insideRange = format.isInsideRange(text);

    if (!covered && !insideRange) {
        return BlockNode.make(false, false);
    }

    return FormattingNode.make(ChildNodeRange.fromNode(text), covered, insideRange);
}

/**
 * Builds a formatting tree starting at node.
 *
 * @returns root of the formatting tree
 */
export function buildFromNode(
    node: Node,
    format: ParseFormat,
    covered: boolean,
): TreeNode | null {
    if (nodeIsText(node) && !textIsNegligible(node)) {
        return buildFromText(node, format, covered);
    } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
        return buildFromElement(node, format, covered);
    } else {
        return null;
    }
}
