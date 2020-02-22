import { useEffect, useState, CSSProperties, memo } from "react";
import { Browser } from "anki/dist/browser";
import React from "react";
import styles from "./BrowseRow.module.css";

interface BrowseRow {
  index: number;
  style: CSSProperties;
  data: BrowserData;
}

interface BrowserData {
  browser: Browser;
}

export const BrowseRow = memo(
  ({ index, style, data: { browser } }: BrowseRow) => {
    const [data, setData] = useState("");

    useEffect(() => {
      const idx = index;
      browser.getRowData(idx, setData);
      console.log(`effect ${index}`);

      return function cleanup() {
        browser.cancelRequest(index);
      };
    }, [index, browser]);

    const onClick = () => {
      browser.selectOnly(index);
      // fixme: better way to trigger refresh
      setData(data + " ");
    };

    const classes = [styles.row];
    if (index % 2) {
      classes.push(styles.rowAlt);
    }
    if (browser.rowIsSelected(index)) {
      classes.push(styles.rowSelected);
    }

    console.log(`render ${index}`);
    return (
      <div style={style} className={classes.join(" ")} onClick={onClick}>
        Item {index}: {data}
      </div>
    );
  }
);
