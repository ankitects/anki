# Security Policy

## Reporting a Vulnerability

Anki does not currently have a bug bounty program, but if you have discovered a
security issue, a private message on our support site would be greatly
appreciated. No account is required to post a message:

https://anki.tenderapp.com/discussion/new

## FAQ

### Javascript on Cards/Templates

Anki allows users and shared deck authors to augment their card designs with
Javascript. This is used frequently, so disabling Javascript by default would
likely break a lot of the shared decks out there. That said, the default may be
changed in the future.

The computer version has a limited interface between Javascript and the parts of
Anki outside of the webview, so arbitrary code execution outside of the webview
should not be possible.

AnkiWeb hosts its study and editing interface on a separate ankiuser.net domain,
so that malicious Javascript on cards can not trigger endpoints hosted on the
main site. If you've found that not to be the case, or found an instance of JS
not being filtered on the main site, please let us know.
