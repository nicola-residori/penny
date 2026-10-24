# M0-03 — Architettura e convenzioni

Stato: implementazione iniziale; da validare e integrare su main.

## Modello
Monolite modulare Spring Boot con API REST, React/Vite separato e PostgreSQL come unica fonte persistente. Niente microservizi. Non sono ancora implementate le funzionalità CRM.

## Backend
- Package radice: `it.penny.api` (conservato per compatibilità col bootstrap).
- Nuove funzionalità: package `it.penny.api.<modulo>`; al loro interno `web`, `application`, `domain`, `persistence` quando necessari, senza creare classi vuote.
- `common` contiene contratti trasversali e gestione errori.
- Controller REST sotto `/api`; JSON; DTO distinti dalle entità JPA; input validati al confine API.
- Eccezioni note mappate esplicitamente a codici HTTP; mai esporre stack trace, SQL o dettagli interni nelle risposte.
- Flyway è l'unico strumento che modifica lo schema; non usare `ddl-auto=update`.

## Frontend
- `src/features/<modulo>` per le feature.
- `src/shared/api` per l'accesso alle API; `src/shared` per componenti e utilità riusabili.
- TypeScript strict; niente chiamate dirette al database.
- La sessione autenticata futura userà cookie sicuri e protezioni CSRF da progettare in M1; il client non gestisce token in localStorage.

## Configurazioni e sicurezza
- `compose.yaml` è per sviluppo locale: porte esposte solo su 127.0.0.1.
- Il deployment on-prem richiederà Dockerfile multi-stage, reverse proxy TLS, secret management, backup e osservabilità; NON considerare la Compose dev pronta per produzione.
- Non introdurre credenziali reali nel repository. `.env` rimane ignorato.

## Contratto errori (iniziale)
`{"code":"VALIDATION_ERROR","message":"Dati della richiesta non validi","timestamp":"..."}`.
Solo gli errori di validazione sono mappati ora; i futuri errori di dominio avranno handler e test dedicati.

## Criteri di verifica
`docker compose run --rm backend mvn -B -ntp verify`
`docker compose run --rm frontend npm test`
`docker compose run --rm frontend npm run build`

## Debito tecnico / passi successivi
- Testcontainers PostgreSQL, test di integrazione reali e test del client frontend.
- DTO e validazione `spring-boot-starter-validation` quando si introduce il primo endpoint con input.
- Sicurezza Spring Security, RBAC e audit in M1.
- Recuperare le specifiche funzionali originali prima di M1.
