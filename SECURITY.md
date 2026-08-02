# Security Policy

## Reporting a Vulnerability

Anki does not currently have a bug bounty program, but if you have discovered a
security vulnerability, please report it privately via GitHub's security
advisory feature:

https://github.com/ankitects/anki/security

For instructions on how to submit a private vulnerability report, see
https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/privately-reporting-a-security-vulnerability

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
