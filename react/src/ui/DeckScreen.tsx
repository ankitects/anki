import React from "react";
import { useState, useEffect } from "react";

import "./DeckScreen.css";
import "./App.css";

import { deckTree, pb as pt } from "anki/dist/backend";

export const DeckDueScreen: React.FC = () => {
  const [decks, setDecks] = useState<pt.IDeckTreeNode[]>([]);

  useEffect(() => {
    if (!decks.length) {
      getTopNode();
    }
  });

  const getTopNode = async () => {
    setDecks(await deckTree());
  };

  const rows = decks.map(deck => (
    <DeckRow key={deck.deckId?.toString()} deck={deck} />
  ));

  return <div className="dueTable">{rows}</div>;
};

type DeckRowProps = {
  deck: pt.IDeckTreeNode;
};

const DeckRow: React.FC<DeckRowProps> = ({ deck }) => {
  const name = deck.names![deck.names!.length - 1];
  const dueCount = deck.reviewCount! + deck.learnCount!;
  const haveChildren = deck.children!.length > 0;
  const indent = deck.names!.length > 1 ? 1 : 0;
  const [collapsed, setCollapsed] = useState(deck.collapsed!);

  const onClick = () => {
    setCollapsed(!collapsed);
    console.log(`collapsed now ${collapsed}`);
  };

  console.log(`drawing ${name}`);

  return (
    <div className="nodeOuter" style={{ marginLeft: indent * 15 + "px" }}>
      <div className="nodeInner">
        {haveChildren ? (
          <button onClick={onClick}>{collapsed ? "+" : "-"}</button>
        ) : (
          <button />
        )}
        {name}
        <div className="counts">
          <span className="due">{dueCount}</span>
          <br />
          <span className="new">{deck.newCount}</span>
        </div>
      </div>
      {collapsed
        ? ""
        : deck.children!.map(deck => (
            <DeckRow key={deck.deckId?.toString()} deck={deck} />
          ))}
    </div>
  );
};
