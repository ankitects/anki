// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";
import { ChildNodeRange } from "./child-node-range";
import type { ElementMatcher, FoundMatch } from "./match-type";
import { applyMatcher } from "./match-type";
import { elementIsNegligible,textIsNegligible } from "./node-negligible";
import type { TreeNode } from "./tree-node";
import { FormattingNode,MatchNode } from "./tree-node";
import { nodeWithinRange } from "./within-range";

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



function merger(before: FormattingNode, after: FormattingNode): before is FormattingNode {
    // TODO introduce a way to provide a custom merger
    // This  will allow any sibling child node ranges to merge
    return true;
}

/**
 * @returns Whether merge suceeded.
 */
function tryMerge(before: FormattingNode, after: FormattingNode): FormattingNode | null {
    return merger(before, after) ? FormattingNode.merge(before, after) : null;
}

/**
 * @param start: The node which is intended to be the new end node
 */
function mergeInNode(
    initial: TreeNode[],
    last: FormattingNode,
): TreeNode[] {
    const minimized: TreeNode[] = [last];

    for (let i = initial.length - 1; i >= 0; i--) {
        const next = initial[i];

        let merged: FormattingNode | null;
        if (next instanceof FormattingNode && (merged = tryMerge(next, last))) {
            minimized[0] = merged;
        } else {
            minimized.unshift(...initial.slice(0, i + 1));
            break;
        }
    }

    return minimized;
}

function appendNode(nodes: TreeNode[], node: TreeNode): TreeNode[] {
    if (node instanceof FormattingNode) {
        return mergeInNode(nodes, node)
    } else {
        return [...nodes, node];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

import { elementIsBlock } from "../../lib/dom";

/**
 * @returns The single formatting node among nodes
 */
function singleFormattingNode(nodes: TreeNode[]): FormattingNode | null {
    if (nodes.length === 1 && nodes[0] instanceof FormattingNode) {
        return nodes[0];
    }

    return null;
}

/**
 * Check whether nodes can be placed above element.
 *
 * @returns Whether nodes be placed above MatchNode of element
 */
function ascender(_node: FormattingNode, matchNode: MatchNode, base: Element): boolean {
    // TODO maybe pass in the match instead? even ascendable could be determined in matcher?
    if (
        elementIsBlock(matchNode.element) ||
        matchNode.element === base ||
        !(matchNode.covered || matchNode.insideRange)
    ) {
        return false;
    }

    // TODO make this configurable from outside
    return true;
}

function tryAscend(node: FormattingNode, matchNode: MatchNode, base: Element) {
    if (ascender(node, matchNode, base)) {
        node.ascendAbove(matchNode);
        return true;
    }

    return false;
}

function buildTreeNode(
    element: Element,
    range: Range,
    matcher: ElementMatcher,
    base: Element,
): TreeNode | null {
    const match = applyMatcher(matcher, element);
    const matches = Boolean(match.type);

    let children: TreeNode[] = [];
    for (const child of element.childNodes) {
        const node = buildFormattingTree(child, range, matcher, matches, base)

        if (node) {
            children = appendNode(children, node);
        }
    }

    const matchNode = MatchNode.make(
        element,
        match,
        matches || children.every((node: TreeNode): boolean => node.covered),
        children.every((node: TreeNode): boolean => node.insideRange),
    );

    if (children.length === 0 && !match.type) {
        return null;
    }

    const formattingNode = singleFormattingNode(children);

    if (formattingNode && tryAscend(formattingNode, matchNode, base)) {
        return formattingNode;
    }

    matchNode.replaceChildren(children);
    return matchNode;
}

function buildFormattingNode(node: Node, range: Range, covered: boolean): FormattingNode | null {
    const insideRange = nodeWithinRange(node, range)

    if (!insideRange && !covered) {
        return null;
    }

    return FormattingNode.make(ChildNodeRange.fromNode(node), insideRange, covered);
}

/**
 * Builds a formatting tree starting at node.
 *
 * @returns root of the formatting tree
 */
export function buildFormattingTree(node: Node, range: Range, matcher: ElementMatcher, covered: boolean, base: Element): TreeNode | null {
    if (nodeIsText(node) && !textIsNegligible(node)) {
        return buildFormattingNode(node, range, covered);
    } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
        return buildTreeNode(node, range, matcher, base);
    } else {
        return null;
    }
}

function previousSibling(node: Node): ChildNode | null {
    return node.previousSibling;
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

function isCoveredFormattingNode(node: TreeNode): node is FormattingNode {
    return node instanceof FormattingNode && node.covered;
}

interface MergeResult {
    node: FormattingNode,
    hitBorder: boolean,
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergePreviousTrees(
    start: FormattingNode,
    container: Node,
    range: Range,
    matcher: ElementMatcher,
    base: Element,
): MergeResult {
    let result = start;

    let sibling = previousSibling(container);
    while (sibling) {
        const siblingNode = buildFormattingTree(sibling, range, matcher, false, base)

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = tryMerge(siblingNode, result))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                }
            }
        }

        sibling = previousSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    }
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergeNextTrees(
    start: FormattingNode,
    container: Node,
    range: Range,
    matcher: ElementMatcher,
    base: Element,
): MergeResult {
    let result = start;

    let sibling = nextSibling(container);
    while (sibling) {
        const siblingNode = buildFormattingTree(sibling, range, matcher, false, base)

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = tryMerge(result, siblingNode))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                }
            }
        }

        sibling = nextSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    }
}

/**
 * Assumes that the range is not placed inside an upper matching element
 */
export function buildTreeFromRange(range: Range, matcher: ElementMatcher, base: Element): TreeNode | null {
    const container = range.commonAncestorContainer;
    const output = buildFormattingTree(container, range, matcher, false, base)

    if (!output) {
        return null;
    }

    if (isCoveredFormattingNode(output)) {
        const previous = mergePreviousTrees(output, container, range, matcher, base);
        const next = mergeNextTrees(previous.node, container, range, matcher, base);
        const parent = container.parentElement;

        if (!previous.hitBorder || !next.hitBorder || !parent) {
            return next.node;
        }

        const matchNode = MatchNode.make(
            parent,
            applyMatcher(matcher, parent),
            next.node.covered,
            next.node.insideRange,
        );

        tryAscend(next.node, matchNode, base);
        return next.node;
    }

    return output;
}

// export function buildTreeFromMatching(node: Node, matcher: ElementMatcher): TreeNode | null {
//     if (nodeIsText(node) && !textIsNegligible(node)) {
//         return FormattingNode.make(ChildNodeRange.fromNode(node));
//     } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
//         return buildTreeNode(node, matcher);
//     } else {
//         return null;
//     }
// }

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
