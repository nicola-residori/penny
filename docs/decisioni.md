# Registro decisioni — Penny

## Fonti e precedenza

Il riferimento funzionale fornito dall’utente è [README_PENNY](sources/README_PENNY.md). [Conformità](conformita-readme.md) e [specifiche consolidate](specifiche-funzionali.md) distinguono requisiti verificati, correzioni successive e assunzioni.

Correzioni esplicite successive: contatto condiviso con almeno un cliente/prospect e sede facoltativa; Prospect→Cliente automatico alla prima vendita, nessun comando manuale; schema cliente ispirato all’allegato; inserimento pregresso senza vendita; dati demo e reset amministrativo con conferma. Origine INTERNAL/THIRD_PARTY non equivale al venditore.

Le note seguenti erano presenti prima del riepilogo consegnato. Non costituiscono da sole una trascrizione completa delle risposte dell’utente. In particolare durata sessioni, provvigioni e processi futuri non vengono dichiarati implementati per effetto di questo riallineamento.

## Registro storico precedente


## Confermate (2026-10-24)
- Un solo sviluppatore iniziale; massimo indicativo 100 utenti.
- Applicazione web, API REST riutilizzabili da eventuale client mobile futuro.
- Sviluppo e runtime in Docker fin dal primo giorno.
- Backend Java 21 + Spring Boot, frontend React + TypeScript + Vite, database PostgreSQL, migrazioni Flyway.
- Monolite modulare; hosting previsto su server aziendale on-premises, eventuale esposizione Internet successiva.
- Autenticazione con Spring Security, sessioni fino a logout o massimo 30 giorni, RBAC granulare globale iniziale e possibile scoping futuro.
- JUnit 5, Testcontainers e Vitest come strategia di test; Testcontainers da integrare nella milestone dedicata.
- Ogni modifica futura tramite script eseguibile; documentazione aggiornata; commit/push solo su conferma.
- Anno provvigionale con inizio 7 febbraio.

## Da definire
- Evento che determina l'anno provvigionale (fattura, ordine, pagamento).
- Base di calcolo provvigioni (lordo, netto, scontato).
- Categorie software eleggibili ed eccezioni.
- Calcolo avanzamento Gantt e permessi modifica date.
- Scoping RBAC per cliente/agente.
- Ulteriori scadenze e canali di notifica.

## Nota di bootstrap
M0-01 prepara soltanto infrastruttura e smoke test, senza implementare funzionalità CRM.

## M0-03 — Convenzioni (2026-10-24)
- Monolite modulare; backend organizzato per modulo funzionale, con separazione web/application/domain/persistence quando serve.
- Frontend organizzato per feature e client API condiviso.
- Risposte di errore con codice stabile, messaggio pubblico e timestamp; non esporre dettagli interni.
- Docker Compose attuale solo per sviluppo: produzione on-prem richiede configurazione separata e hardening.
- `*.tsbuildinfo` esclusi da Git.
