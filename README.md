# Penny — Il CRM aziendale

Penny è un CRM web per una PMI che vende software e hardware, eroga formazione e gestisce progetti tecnici di lungo periodo.

## Significato del nome
- **P** — Projects: progetti tecnici e Gantt
- **E** — Engagements: attività commerciali
- **N** — Notifications: scadenze licenze, contratti e garanzie
- **N** — New deals: vendite software/hardware
- **Y** — Yearly commissions: regole annuali, con anno dal 7 febbraio

## Stato
**M0-01 — Bootstrap generato, verifica runtime e test ancora da eseguire.**

## Stack
Java 21, Spring Boot REST, PostgreSQL 17, Flyway, React, TypeScript, Vite. Monolite modulare; Docker Compose per sviluppo locale; futuro deploy su server aziendale on-premises. Target circa 100 utenti. Autenticazione e RBAC saranno introdotti nelle milestone successive.

## Avvio locale
Prerequisito: Docker Desktop avviato.

```bash
docker compose up -d
docker compose ps
docker compose logs -f backend
```

Frontend: http://localhost:5173
Backend: http://localhost:8080/api/health

Stop: `docker compose down` (non elimina i dati). Non usare `down -v` se vuoi conservarli.

## Test
```bash
docker compose run --rm backend mvn test
docker compose run --rm frontend npm test
```

## Roadmap
- M0: repository, container, database, CI
- M1: utenti, ruoli, clienti, sedi, contatti, documenti, audit
- M2: catalogo, vendite aggregate, installato
- M3: attività commerciali, offerte, contratti, notifiche
- M4: progetti tecnici, task, dipendenze, milestone, Gantt
- M5: provvigioni e dashboard
- M6: test, hardening, release

## Principi funzionali
- Catalogo prodotti, vendite e installazioni sono entità distinte.
- Le allocazioni alle postazioni non modificano le quantità vendute.
- Le scadenze derivano dalle entità originali, evitando duplicazioni.
- Attività commerciali e tecniche sono distinte.
- Le modifiche significative devono essere tracciate.
- Fonte dati unica; inserimento manuale iniziale, nessuna integrazione ERP.

## Decisioni e questioni aperte
Vedi `docs/decisioni.md`.

## Specifiche originali
**Da reintegrare:** il README funzionale integrale e `docs/specifiche-funzionali.md` non erano presenti nel repository iniziale. Questo bootstrap conserva i requisiti noti senza sostituire il documento originale.

## M0-02 — CI e controlli automatici

Workflow: `.github/workflows/ci.yml`. Esegue test Maven su Java 21, test e build frontend su Node 22 e controlli base per evitare il versionamento di `.env` e artefatti generati. Dettagli e limiti: `docs/m0-02-ci.md`.

Stato M0-02: **implementata, in attesa della verifica dei workflow su GitHub**. La milestone si chiude dopo il primo esito verde delle Actions.
