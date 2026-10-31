# Confronto con l’anno precedente e storico demo

Richiesta già definita e autorizzata in autonomia: switch inizialmente spento, sovrapposizione dell’anno precedente nei tre grafici e scostamento per punto; dati demo aggiuntivi su circa due anni, cancellabili con il reset amministrativo esistente.

1. Backend analytics: query opzionale comparePreviousYear, stessi filtri/RBAC e snapshot di lettura. Ogni bucket viene confrontato con le sue date effettive, tagliate al filtro e spostate di un anno civile. Le settimane e gli span multipli non vengono riallineati a bucket diversi. Il solo 29 febbraio senza equivalente non viene confrontato. Importi e scostamenti restano decimali esatti; percentuale nulla con base zero.
2. Frontend: switch immediato sui filtri applicati; linea precedente tratteggiata distinta, scala comune, legenda, dettaglio accessibile con entrambi i valori e gap assoluto/percentuale. Tabella esportabile visivamente coerente con le serie.
3. Demo v3: aggiunta idempotente di un cohort marcato DEMO con vendite/ordini, nuove acquisizioni e stagionalità su 24 mesi. Nessuna riscrittura dei dati esistenti; reset completo protetto da RESET, utenti/permessi preservati.
4. Test significativi sul server CT111: default spento, clipping/span/leap day, importi precisi, base zero, permessi e filtro cliente; UI overlay/toggle; demo upgrade/idempotenza/reset. Build sul server, backup, rilascio dedicato senza merge; caricamento demo autorizzato esplicitamente dall’utente, mai reset automatico.

Verifica CT111: 175 test backend e 138 frontend superati, TypeScript e build Vite riusciti. La suite verifica anche aggiornamento demo v1/v2 a v3, rollback, caricamento idempotente e reset con conservazione di utenti e configurazioni.
