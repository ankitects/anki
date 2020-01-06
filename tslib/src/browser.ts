import { findCards, browserRows } from "./backend";
import { expectNotNull } from "./tsutils";

const BATCH_SIZE = 50;

type DataCallback = (data: string) => void;

interface InFlightRequest {
  kind: "inflight";
  callback?: DataCallback;
}

interface FinishedRequest {
  kind: "finished";
  data: string;
}

type DataState = InFlightRequest | FinishedRequest;

export class Browser {
  /** The list of card IDs returned by a search request. */
  private cardIds: number[];

  private selectedIds: Set<number> = new Set();

  /** Requests that are not yet dispatched, and can be canceled.
   * Maps row -> callback
   */
  private newDataRequests: Map<number, DataCallback> = new Map();

  /** Map of card IDs to pending|complete data. */
  private cardData: Map<number, DataState>;
  /** If a request is currently active. */
  private cardDataRequestInFlight = false;

  /** Timestamp of the last request sending time. */
  private lastRequestTime = 0;

  private dispatchTimer: number | undefined;

  constructor() {
    this.cardIds = [];
    this.cardData = new Map();
  }

  /** Performs a search with the provided string. */
  async search(text: string): Promise<void> {
    this.cardIds = await findCards(text);
    this.cardData = new Map();
    this.selectedIds = new Set();
  }

  /** The number of rows returned by a search. */
  rows(): number {
    return this.cardIds.length;
  }

  /** Calls callback with data associated with provided row, fetching if necessary. */
  getRowData(row: number, callback: DataCallback): void {
    const state = this.dataStateFromRow(row);
    if (state) {
      if (state.kind === "finished") {
        callback(state.data);
      } else if (state.kind === "inflight") {
        // a prefetch request may not have a callback registered
        if (!state.callback) {
          state.callback = callback;
        }
      }
      return;
    }

    this.newDataRequests.set(row, callback);
    this.maybeDispatch();
  }

  rowIsSelected(row: number): boolean {
    const cid = this.cardIds[row];
    return this.selectedIds.has(cid);
  }

  toggleRowSelected(row: number): void {
    const cid = this.cardIds[row];
    if (this.selectedIds.has(cid)) {
      this.selectedIds.delete(cid);
    } else {
      this.selectedIds.add(cid);
    }
  }

  selectOnly(row: number): void {
    const cid = this.cardIds[row];
    this.selectedIds.clear();
    this.selectedIds.add(cid);
  }

  /** Cancel a request for the given row.
   * Rows that scroll off screen can avoid unnecessary work this way.
   */
  cancelRequest(row: number): void {
    this.newDataRequests.delete(row);
    const state = this.dataStateFromRow(row);
    if (state && state.kind === "inflight") {
      state.callback = undefined;
    }
  }

  private dataStateFromRow(row: number): DataState | undefined {
    const cid = this.cardIds[row];
    return this.cardData.get(cid);
  }

  private setDataStateForRow(row: number, state: DataState): void {
    const cid = this.cardIds[row];
    this.cardData.set(cid, state);
  }

  /** Fire a new request if none is active, and the time/size limits have been reached. */
  private maybeDispatch(): void {
    const sendAfterMillis = 100;

    // everything cancelled?
    if (this.newDataRequests.size === 0) {
      return;
    }

    if (!this.lastRequestTime) {
      this.lastRequestTime = new Date().getTime();
    }
    const millisSince = new Date().getTime() - this.lastRequestTime;

    // time to fire off a new request?
    if (
      (this.newDataRequests.size === BATCH_SIZE ||
        millisSince > sendAfterMillis) &&
      !this.cardDataRequestInFlight
    ) {
      this.lastRequestTime = new Date().getTime();
      this.dispatchRequestBatch();
    } else {
      // check again in 100ms
      if (!this.dispatchTimer) {
        this.dispatchTimer = window.setTimeout(() => {
          this.dispatchTimer = undefined;
          this.maybeDispatch();
        }, 100);
      }
    }
  }

  /** If a batch is small, add extra requests for rows above or below the requested row. */
  private addPrefetchIds(cids: number[], rows: number[]): void {
    const scrollingDown = rows.length < 2 || rows[0] < rows[1];
    let lastRow = rows[rows.length - 1];
    while (rows.length < BATCH_SIZE) {
      lastRow += scrollingDown ? 1 : -1;
      if (lastRow < 0 || lastRow >= this.cardIds.length) {
        break;
      }
      if (this.dataStateFromRow(lastRow)) {
        // already in flight or received
        break;
      }
      if (rows.indexOf(lastRow) !== -1) {
        // already in batch
        break;
      }
      console.log(`adding extra prefetch ${lastRow}`);
      cids.push(this.cardIds[lastRow]);
      rows.push(lastRow);
      this.setDataStateForRow(lastRow, { kind: "inflight" });
    }
  }

  /** Request a batch of pending card objects. */
  private dispatchRequestBatch(): void {
    const cids: number[] = [];
    const rows: number[] = [];
    // fixme: reverse order, limit to batch size
    this.newDataRequests.forEach((cb, row) => {
      this.setDataStateForRow(row, { kind: "inflight", callback: cb });
      rows.push(row);
      cids.push(this.cardIds[row]);
    });
    this.newDataRequests.clear();
    this.addPrefetchIds(cids, rows);
    this.cardDataRequestInFlight = true;
    browserRows(cids)
      .then(res => {
        this.cardDataRequestInFlight = false;
        this.onRequestBatchReceived(res, rows);
      })
      .catch(err => {
        this.cardDataRequestInFlight = false;
        throw Error(`failed to fetch browser row: ${err}`);
      });
  }

  /** Save received data, notifying interested parties. */
  private onRequestBatchReceived(data: string[], rows: number[]): void {
    data.forEach((val, n) => {
      const row = rows[n];
      const singleData = data[n];
      const state = expectNotNull(this.dataStateFromRow(row));
      if (state.kind === "inflight" && state.callback) {
        state.callback(singleData);
      }
      this.setDataStateForRow(row, { kind: "finished", data: singleData });
    });
  }
}
