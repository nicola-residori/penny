# M1-06 — Catalogo prodotti

## Stato verificato prima dello sviluppo
M1-01: autenticazione, CSRF, sessioni, cambio password e rate limiting presenti; V100 applicata.
M1-02: utenti, ruoli e override ALLOW/DENY presenti e confrontati con archivio originale; V101 applicata.
M1-03: navigazione, dashboard e CSS presenti; integrazioni successive conservate.
M1-04: clienti e contatti presenti; V102 applicata.
M1-05: prodotti installati presenti; V103 applicata. Correzione paginazione preservata.
Queste milestone sono modifiche locali non committate rispetto a main; non sono state sostituite con la versione remota M0.
Controlli iniziali: 5 test backend, 1 test frontend e build UI superati in Docker.

## Comportamento
Catalogo `/api/catalog-products`: elenco paginato (page 0-based, size 1–100), ricerca letterale su codice/nome/produttore, filtro origin INTERNAL/THIRD_PARTY e active true/false; GET dettaglio, POST e PUT. Nessuna cancellazione fisica: archiviare con active=false.
Codice obbligatorio e univoco senza distinzione maiuscole/minuscole. Nome e categoria obbligatori, produttore/modello/descrizione opzionali.
Le installazioni `/api/installed-products` includono origin e catalogProductId. INTERNAL richiede il collegamento; THIRD_PARTY lo consente senza obbligo. Origine e categoria devono corrispondere alla voce collegata; il database applica gli stessi vincoli tramite FK composita e CHECK. I client devono inviare esplicitamente origin.
La selezione UI precompila nome, categoria, produttore e modello. Nome/produttore/modello restano snapshot modificabili, senza aggiornamento retroattivo quando cambia il catalogo. La categoria resta coerente con il collegamento.
Una voce archiviata non può essere collegata a nuove installazioni; i riferimenti precedenti restano validi e modificabili. Lettura catalogo necessaria per assegnare un nuovo collegamento; un utente con PRODUCTS_WRITE può mantenere un riferimento esistente anche senza CATALOG_READ. I dati del catalogo non sono inclusi nelle risposte delle installazioni.
Archiviazione e assegnazione bloccano la stessa riga catalogo, serializzando le operazioni concorrenti.

## RBAC
| Ruolo iniziale | CATALOG_READ | CATALOG_WRITE |
| --- | --- | --- |
| ADMIN | sì | sì |
| SALES | sì | sì |
| TECH | sì | no |
Ruoli personalizzati non ricevono accessi automatici. Gli override e le precedenze ALLOW/DENY restano quelli di RbacService. PRODUCTS_READ/WRITE restano separati. `/api/auth/me` espone canReadCatalog/canWriteCatalog.

## Migrazione e rilascio
V104 è additiva; V1 e V100–V103 non sono modificate. Tutti i prodotti installati esistenti diventano THIRD_PARTY con catalogProductId nullo; gli altri campi, gli ID e le date rimangono identici. Scelta approvata dall'utente: non si inferisce l'origine storica.
Prima di applicare la migrazione all'istanza di sviluppo effettuare un backup PostgreSQL con pg_dump; non eliminare volumi e non usare Flyway clean. Non rimuovere V104 dopo l'applicazione. Eventuali correzioni successive richiedono una nuova migrazione.
Eseguire `bash scripts/verify-m1-06.sh`: lo script copia i sorgenti in una directory temporanea, avvia PostgreSQL isolato, esegue Maven, Vitest e build UI, quindi elimina solo le proprie risorse di test. Non richiede .env e non tocca database o target/classes del backend attivo. I test di integrazione PostgreSQL richiedono PENNY_TEST_JDBC_URL, PENNY_TEST_DB_USER e PENNY_TEST_DB_PASSWORD (la configurazione Compose li imposta); una semplice esecuzione Maven senza queste variabili li salta.
La CI GitHub Actions avvia PostgreSQL 17 e imposta le variabili dei test, così i test di integrazione vengono eseguiti anche nelle verifiche automatiche.
Il rilascio applicativo richiede il riavvio del backend con la nuova build; Flyway applica V104 all'avvio. Nessun commit, push o merge automatico.

## Verifica finale e limiti
15 test backend, inclusi 10 test PostgreSQL/API, e 7 test frontend superati; build TypeScript/Vite superata. Upgrade V103→V104 confrontato campo per campo su una riga preesistente. Revisione indipendente conclusa e regressione sul selettore corretta con test. Il database applicativo attivo resta a V103 fino al riavvio con nuova build.
Npm audit segnala tre vulnerabilità nelle dipendenze di sviluppo già presenti (Vitest 3.2.7, @vitest/mocker e tinypool): due critiche e una moderata. Le versioni coinvolte non sono state introdotte da M1-06; la correzione proposta da npm richiede un aggiornamento maggiore del test runner, lasciato fuori da questa milestone.
