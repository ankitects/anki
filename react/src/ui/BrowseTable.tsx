import React from "react";
import { FixedSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import { Browser } from "anki/dist/browser";
import { BrowseRow } from "./BrowseRow";

interface BrowseTableProps {
  browser: Browser | null;
}

export const BrowseTable: React.FC<BrowseTableProps> = ({ browser }) => {
  if (!browser) {
    return <div />;
  } else {
    return (
      <AutoSizer>
        {({ height, width }) => (
          <div tabIndex={0}>
            <FixedSizeList
              height={height}
              itemCount={browser.rows()}
              itemSize={35}
              width={width}
              itemData={{ browser }}
            >
              {BrowseRow}
            </FixedSizeList>
          </div>
        )}
      </AutoSizer>
    );
  }
};
