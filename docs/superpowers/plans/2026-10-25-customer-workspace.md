# Customer workspace implementation plan

Goal: customer sheet inspired by the supplied layout, customer sites, legacy hardware/software entry, distinct third-party badges, warranty/license calendar and manual empty trash.

Constraints: preserve all local M1/M2 patches and user data. Additive V107/V108 only. No merge, commit or live empty-trash invocation. User authorized autonomous choices.

- [x] Finish isolated empty-trash implementation with ADMIN purge permission, irreversible confirmation, per-module authorization and protected-row retention.
- [x] Add customer sites, same-customer database integrity and permission-filtered customer workspace/deadline endpoints.
- [x] Add customer tabs, metrics, sites forms and scoped hardware/software registration without sale.
- [x] Add monthly calendar, date navigation, accessible origin badges; rename visible Postazioni to Hardware, preserve database/API compatibility.
- [x] Verify backend integration suite, frontend suite/build and independent review. Back up actual source/database before applying verified patch; preserve all existing rows. Restart app without invoking destructive endpoints.

Review focus: unauthorized modules must not leak details; site links cannot cross customer boundaries; deleted records excluded; empty trash honors references; customer scoped product creation never switches customer silently.
