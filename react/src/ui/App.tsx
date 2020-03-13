import "./App.css";
import React from "react";
import { Switch, Route, Link, HashRouter } from "react-router-dom";
import { DeckDueScreen } from "./DeckScreen";
import { BrowseScreen } from "./BrowseScreen";

export default function App() {
  return (
    <HashRouter hashType="slash">
      <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
        <ul>
          <li>
            <Link to="/decks">Decks</Link>
          </li>
          <li>
            <Link to="/browse">Browse</Link>
          </li>
        </ul>

        <hr />

        <Switch>
          <Route exact path="/decks">
            <DeckDueScreen />
          </Route>
          <Route path="/browse">
            <BrowseScreen />
          </Route>
        </Switch>
      </div>
    </HashRouter>
  );
}
