## Unreleased

- Added FSRS7 scheduling for new presets and for existing presets with no saved parameters. Presets with saved legacy parameters keep their current model until the parameters are reset or optimized.
- FSRS7 uses both memory components for recall searches, graphs, review ordering and the no-review workload estimate. The Stability shown in the browser and returned to add-ons is the interval at which predicted recall reaches 90%. Retrievability sort orders reviews while preserving learn-ahead and the configured placement of interday learning cards.
- FSRS7 upgrades can recover cards whose original deck was deleted. Repair checks memory states only for cards whose home preset is eligible.
- Recall searches, graphs and ordering use the Default preset when a card's home deck or preset is missing. Deck Options shows saved FSRS-4.5 and FSRS-5 parameters when the newer parameter slots are empty.
- Legacy SQL recall functions use the displayed Stability as the interval at which predicted recall reaches 90%.
