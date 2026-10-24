# Registro decisioni — Penny

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
