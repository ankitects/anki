// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";
import { nodeWithinRange } from "./within-range";
import type { ElementMatcher, FoundMatch } from "./match-type";
import type { TreeNode } from "./tree-node";
import { MatchNode, FormattingNode } from "./tree-node";
import { applyMatcher } from "./match-type";
import { ChildNodeRange } from "./child-node-range";
import { textIsNegligible, elementIsNegligible } from "./node-negligible";

/**
 * @returns Split text node to end direction
 */
function splitText(node: Text, offset: number): Text {
    return node.splitText(offset);
}

// TODO maybe both start and end should be of type Node
// Could also probably be the new "Range anchors"
interface SplitRange {
    /**
     * Used to recreate a range: `range.setStartBefore(start)`
     */
    start: Text | null;
    /**
     * Used to recreate a range: `range.setEndAfter(end)`
     */
    end: Text | null;
}

export function splitPartiallySelectedTextNodes(range: Range): SplitRange {
    const startContainer = range.startContainer;
    const startOffset = range.startOffset;

    // TODO Maybe we should avoid splitting, if they
    // create zero-length text nodes

    const start = nodeIsText(startContainer)
        ? splitText(startContainer, startOffset)
        : null;

    const endContainer = range.endContainer;
    const endOffset = range.endOffset;

    let end: Text | null = null;
    if (nodeIsText(endContainer)) {
        const splitOff = splitText(endContainer, endOffset);

        if (splitOff.data.length === 0) {
            // Range should include the split-off text if it is zero-length.
            // For the start container, this is done automatically.
            end = splitOff;
            range.setEndAfter(end);
        } else {
            end = endContainer;
        }
    }

    return { start, end };
}

function findWithinNodeInner(
    node: Node,
    matcher: ElementMatcher,
    matches: FoundMatch[],
): void {
    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }

        const match = applyMatcher(matcher, node);
        if (match.type) {
            matches.push({ match, element: node as HTMLElement | SVGElement });
        }
    }
}

/**
 * Will not include parent node
 */
export function findWithinNode(node: Node, matcher: ElementMatcher): FoundMatch[] {
    const matches: FoundMatch[] = [];

    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }
    }

    return matches;
}


////////////////////////////////////////////////////////////////////////////////////////////////



function merger(before: TreeNode, after: FormattingNode): before is FormattingNode {
    // TODO introduce a way to provide a custom merger

    // This  will allow any sibling child node ranges to merge
    return before instanceof FormattingNode;
}

/**
 * @param start: The node which is intended to be the new end node
 */
function mergeInNode(
    initial: TreeNode[],
    last: TreeNode,
): TreeNode[] {
    const merged: TreeNode[] = [];

    if (!(last instanceof FormattingNode)) {
        merged.push(...initial, last);
        return merged;
    }

    let rightmost: FormattingNode = last;
    for (let i = initial.length - 1; i >= 0; i--) {
        let next = initial[i];

        // TODO see merger
        if (merger(next, rightmost)) {
            next.mergeWith(rightmost);
        } else {
            merged.unshift(...initial.slice(0, i + 1));
            break;
        }
    }

    return merged;
}


////////////////////////////////////////////////////////////////////////////////////////////////

import { elementIsBlock } from "../../lib/dom";

/**
 * @returns The single formatting node among nodes
 */
function singleFormattingNode(nodes: TreeNode[]): FormattingNode | null {
    if (nodes.length === 0 && nodes[0] instanceof FormattingNode) {
        return nodes[0];
    }

    return null;
}

/**
 * Check whether nodes can be placed above element.
 *
 * @returns Whether nodes be placed above MatchNode of element
 */
function ascender(_node: FormattingNode, element: Element): boolean {
    // TODO maybe pass in the match instead? even ascendable could be determined in matcher?
    if (elementIsBlock(element)) {
        return false;
    }

    // TODO make this configurable from outside
    return true;
}


function buildTreeNode(element: Element, matcher: ElementMatcher): TreeNode {
    let children: TreeNode[] = [];

    for (const child of element.childNodes) {
        const node = buildFormattingTree(child, matcher)

        if (node) {
            children = mergeInNode(children, node)
        }
    }

    const matchNode = MatchNode.make(element, applyMatcher(matcher, element));
    const formattingNode = singleFormattingNode(children);

    if (formattingNode && ascender(formattingNode, element)) {
        formattingNode.ascendAbove(matchNode);
        return formattingNode;
    }

    matchNode.replaceChildren(children);
    return matchNode;
}

/**
 * Builds a formatting tree starting at node.
 *
 * @remarks
 * Tree will be in its initial shape, which means all text nodes will have a
 * formatting node as their parent, and each formatting node will have a single
 * text node as their child.
 *
 * @returns root of the formatting tree
 */
export function buildFormattingTree(node: Node, matcher: ElementMatcher): TreeNode | null {
    if (nodeIsText(node) && !textIsNegligible(node)) {
        return FormattingNode.make(ChildNodeRange.fromNode(node));
    } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
        return buildTreeNode(node, matcher);
    } else {
        return null;
    }
}



/**
 * @returns Text nodes contained in node in source order
 */
// TODO -> built match tree at that point
export function findTextsWithinNode(node: Node): Text[] {
    if (nodeIsText(node)) {
        return [node];
    } else if (nodeIsElement(node)) {
        const nodes: Text[] = [];

        for (const child of node.childNodes) {
            nodes.push(...findTextsWithinNode(child));
        }

        return nodes;
    } else {
        return [];
    }
}

/**
 * @returns Text nodes contained in range in source order
 */
export function findTextsWithinRange(range: Range): Text[] {
    return findTextsWithinNode(range.commonAncestorContainer).filter(
        (text: Text): boolean => nodeWithinRange(text, range),
    );
}

/**
 * We do not want to surround zero-length text nodes
 */
export function validText(text: Text): boolean {
    return text.length > 0;
}
