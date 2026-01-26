// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsEmpty, nodeIsElement, nodeIsText } from "@tslib/dom";

import type { Match } from "../match-type";
import type { TreeNode } from "../tree";
import { BlockNode, ElementNode, FormattingNode } from "../tree";
import { appendNode } from "./add-merge";
import type { BuildFormat } from "./format";

function buildFromElement<T>(
    element: Element,
    format: BuildFormat<T>,
    matchAncestors: Match<T>[],
): TreeNode[] {
    const match = format.createMatch(element);

    if (match.matches) {
        matchAncestors = [...matchAncestors, match];
    }

    let children: TreeNode[] = [];
    for (const child of [...element.childNodes]) {
        const nodes = buildFromNode(child, format, matchAncestors);

        for (const node of nodes) {
            children = appendNode(children, node, format);
        }
    }

    if (match.shouldRemove()) {
        const parent = element.parentElement!;

        for (const child of children) {
            if (child instanceof FormattingNode) {
                if (child.hasMatchHoles) {
                    child.matchLeaves.push(match);
                    child.hasMatchHoles = false;
                }

                child.range.rebaseIfParentIs(element, parent);
            }
        }

        format.announceElementRemoval(element);
        element.replaceWith(...element.childNodes);
        return children;
    }

    const matchNode = ElementNode.make(
        element,
        children.every((node: TreeNode): boolean => node.insideRange),
    );

    if (children.length === 0) {
        // This means there are no non-negligible children
        return [];
    } else if (children.length === 1) {
        const [only] = children;

        if (
            // blocking
            only instanceof BlockNode
            // ascension
            || (only instanceof FormattingNode && format.tryAscend(only, matchNode))
        ) {
            return [only];
        }
    }

    matchNode.replaceChildren(...children);
    return [matchNode];
}

function buildFromText<T>(
    text: Text,
    format: BuildFormat<T>,
    matchAncestors: Match<T>[],
): FormattingNode<T> | BlockNode {
    const insideRange = format.isInsideRange(text);

    if (!insideRange && matchAncestors.length === 0) {
        return BlockNode.make();
    }

    return FormattingNode.fromText(text, insideRange, matchAncestors);
}

function elementIsNegligible(element: Element): boolean {
    return elementIsEmpty(element);
}

function textIsNegligible(text: Text): boolean {
    return text.length === 0;
}

/**
 * Builds a formatting tree starting at node.
 *
 * @returns root of the formatting tree
 */
export function buildFromNode<T>(
    node: Node,
    format: BuildFormat<T>,
    matchAncestors: Match<T>[],
): TreeNode[] {
    if (nodeIsText(node) && !textIsNegligible(node)) {
        return [buildFromText(node, format, matchAncestors)];
    } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
        return buildFromElement(node, format, matchAncestors);
    } else {
        return [];
    }
}
