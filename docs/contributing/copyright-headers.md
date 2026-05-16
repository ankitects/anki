# Copyright headers

AnkiDroid uses git history as the authoritative record of authorship. 
You own the copyright of your contributions, regardless of the copyright header. 
Copyright headers are optional

Please contact the maintainers, or raise an issue if you have any questions/concerns.

## Applying a copyright header

You may add a copyright header to work which you have created or nontrivially modified. 
Your name may be a pseudonym, and the email is optional.

The copyright line should be added at the end of the header and it is recommended to be formatted as follows:

```diff
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2023 Existing Contributor <email@example.com>
// SPDX-FileCopyrightText: 2026 New Contributor Name <email@example.com>

package com.ichi2.anki
```

Alternate formats are listed: https://reuse.software/faq/#copyright-symbol 

You may request this header be added to your work retroactively (ideally via a pull request).

## Collective copyright

The project adds a collective copyright line to `.kt` files for license-compliance tooling:

`SPDX-FileCopyrightText: Contributors to the AnkiDroid project`

This is informational only and does not affect your copyright. See [REUSE.toml](../../REUSE.toml) for the implementation.

## Removing copyright headers

Existing copyright notices must be preserved **as-is** and **must not** be removed.  The only exception is when this copyright header is your own.
