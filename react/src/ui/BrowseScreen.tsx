import React, { useState } from "react";
import { Browser } from "anki/dist/browser";
import { BrowseTable } from "./BrowseTable";
import { BrowseSearchInput } from "./BrowseSearchInput";
import SplitPane from "react-split-pane";

export const BrowseScreen = () => {
  const [browser, setBrowser] = useState<Browser | null>(null);

  const onSearchChanged = (txt: string) => {
    const s = new Browser();
    setBrowser(null);
    s.search(txt)
      .then(() => setBrowser(s))
      .catch(err => {
        throw Error(err);
      });
  };

  console.log("render browser");

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <div>
        <BrowseSearchInput onSearchChanged={onSearchChanged} />
      </div>
      <div style={{ position: "relative", height: "100%" }}>
        <SplitPane split="horizontal" minSize={50} defaultSize={500}>
          <div style={{ flexBasis: "100%" }} tabIndex={0}>
            <BrowseTable browser={browser} />
          </div>
          <div>Editing Area</div>
        </SplitPane>
      </div>
    </div>
  );
};
