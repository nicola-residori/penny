# Confronto README utente → CRM

Fonte integra: [README_PENNY](sources/README_PENNY.md), ricevuta il 26 ottobre 2026. Prevalgono inoltre le correzioni successive su contatti, sedi, origine dei dati e conversione automatica. Stato tecnico del riepilogo storico non sovrascrive migrazioni e funzioni già implementate.

| Decisione del README | Riscontro e modifica | Verifica |
|---|---|---|
| Catalogo separato da installazioni | Entità e API distinte conservate | Integrazione catalogo/installato |
| INTERNAL obbligatoriamente da catalogo | Invarianti applicative e FK conservate | Catalog integration |
| THIRD_PARTY catalogo facoltativo | Inserimento diretto/pregresso preservato | Catalog + operational fields |
| Non attribuire INTERNAL ai dati storici | V104 conservata immutata | Migrazione dati storici |
| Codice, nome, descrizione, tipo, produttore, stato | Già presenti | Catalog panel/integration |
| Prezzo e dati economici catalogo | V110 prezzo EUR opzionale, validazione decimale esatta, null/omesso distinti | Catalog commercial unit/integration |
| Fornitore distinto dal produttore | V110 catalogo e V111 installato; campi UI separati | Commercial e operational fields |
| Hardware/software/servizio/formazione | TRAINING introdotto nel catalogo; non installabile | Catalog training/filter integration |
| Quantità o utenti attivati per installazione | V111 aggiuntiva, nessuna quantità storica inventata | Installed operational fields |
| Prodotti di terzi rivenduti | Rimangono THIRD_PARTY; corrette le etichette che confondevano origine con acquisto | UI/render e flussi demo |
| Servizi erogati distinti da beni installati | Formazione esclusa dai selettori installabili; gestione erogazione futura dichiarata; SERVICE legacy conservato | Backend rifiuta TRAINING; catalogo installable |
| Clienti aziende/privati, contatti e archiviazione | Funzioni conservate; contatti condivisi/sedi/stato da correzioni successive | Customer/shared contacts/migration |
| Paginazione singola pagina | Solo conteggio; componente comune per liste aggiunte | Pagination e panels |
| Controlli affiancati e pagina corrente su più pagine | Componente comune, lookup e admin utenti inclusi | Pagination/panels, M2 regressions |
| Login/sessioni/BCrypt/CSRF/logout | Verifiche con filtro Security reale; nessun bean duplicato | 6 security integration cases |
| Discrepanza login senza token 401/403 | Corretto il dispatch ERROR che trasformava403 in401; una richiesta anonima diretta a /error resta protetta | ReadmeHttpSecurityTest + ReadmeSecurityIntegrationTest |
| RBAC e revoca sessione | Conservati e verificati disable/reset/force change | Security e Rbac tests |
| Nessuna perdita dati/migrazioni incrementali | V100–V109 immutate; V110–V112 additive | Migration tests + confronto DB attivo prima/dopo |
| Un branch dedicato, niente merge automatico | codex/readme-alignment; patch locali precedenti conservate | Git locale |
| Remoto rispecchia tutte le patch | Verifica: main remoto ancora al bootstrap M0, patch locali non pubblicate | Non eseguiti commit/push/merge: resta attività di revisione/pubblicazione |
| M2 non ancora dettagliata | Rimossa l'affermazione che una M2 assunta equivalga a tutti i processi concordati | README e specifica aggiornati |
| UI coerente con permessi e modello | Etichette origine corrette; installazioni/attivazioni e fornitore/quantità espliciti; sezione admin dati dedicata | Test UI e build |
| Successiva richiesta dati demo/reset | V112, DATA_MANAGE, seed idempotente, reset con frase e sicurezza conservata | Demo integration, UI, rollback e reset completo |

Le verifiche di attivazione e i conteggi effettivi sono nel report di consegna. Nessun esito di GitHub Actions viene dedotto dai test locali. I moduli futuri non specificati nel README non vengono dichiarati completati.
