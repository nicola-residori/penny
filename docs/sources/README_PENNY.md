# Penny — Decisioni di progetto e stato dello sviluppo

> Documento di passaggio di consegne, aggiornato al 25 ottobre 2026. Riassume le scelte e le verifiche riportate nella conversazione. Non sostituisce un'ispezione del codice o del database attuali.

## 1. Obiettivo del progetto

**Penny** è un CRM per un'attività che fornisce software, hardware, formazione e servizi tecnici. Deve gestire utenti e autorizzazioni, clienti, catalogo commerciale, prodotti effettivamente installati o attivati presso i clienti e, in successive milestone, processi operativi quali assistenza, contratti, rinnovi, preventivi e ordini.

La distinzione fondamentale è tra **ciò che l'azienda offre** (catalogo) e **ciò che un determinato cliente utilizza o possiede** (prodotti installati/attivati).

## 2. Stack e repository

| Componente | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.7 |
| Frontend | React 19, TypeScript, Vite |
| Database | PostgreSQL 17 |
| Migrazioni | Flyway |
| Esecuzione locale | Docker Compose |
| Build e test backend | Maven |
| Repository | `https://github.com/nicola-residori/penny` |
| Directory locale Mac | `~/Develop/penny` |
| Frontend locale | `http://localhost:5173` |
| Backend locale | `http://localhost:8080` |

Le porte sono esposte localmente su `127.0.0.1` nell'ambiente verificato.

## 3. Metodo di sviluppo concordato

### Metodo usato durante M1

- Pacchetti ZIP con script `run.sh`, installati sul Mac.
- Backup dei sorgenti prima delle modifiche; build frontend, compilazione/test backend e riavvio dei container quando previsti dallo script.
- Preferenza per **un unico comando terminale copiabile**, senza modifiche manuali ai file.
- Le migrazioni Flyway sono incrementali: **il ripristino dei sorgenti non annulla una migrazione già applicata al database**.

### Metodo scelto per le prossime milestone

- Passaggio a **Work/Codex** per lavorare direttamente sul repository.
- Un branch dedicato per ogni milestone; per M1-06 è stato proposto `feature/m1-06-product-catalog`.
- Prima di modificare il codice, controllare che il repository remoto rifletta tutte le patch applicate localmente fino a M1-05, inclusa la rifinitura della paginazione.
- Analizzare lo stato effettivo del codice, implementare, eseguire build e test e presentare il riepilogo delle modifiche.
- **Non effettuare merge automatico su `main`** senza revisione.

## 4. Milestone M1: stato

| Milestone | Ambito | Stato riportato |
| --- | --- | --- |
| M1-01 | Autenticazione e sicurezza | Operativa; resta una discrepanza tecnica da verificare |
| M1-02 | Utenti, ruoli e permessi | Funzionalmente verificata |
| M1-03 | Dashboard, navigazione e responsive UI | Funzionalmente verificata |
| M1-04 | Gestione clienti | Funzionalmente verificata |
| M1-05 | Prodotti installati | Build, migrazione e prove funzionali riuscite |
| M1-06 | Catalogo prodotti e integrazione con installazioni | **Da implementare** |

### M1-01 — Autenticazione

Decisioni e funzionalità previste/implementate:

- Login con sessione; password cifrate con BCrypt.
- Protezione CSRF, limitazione dei tentativi di login, logout e cambio password.
- Bootstrap dell'utente amministratore.
- Test riportati: endpoint health accessibile; richieste anonime protette; cambio password senza CSRF respinto; logout che invalida la sessione; rate limiting dopo tentativi errati.

**Punto da verificare:** in un test, il login senza token CSRF restituiva `401` anziché il `403` atteso. La richiesta risultava comunque respinta. Non considerare risolta questa discrepanza senza un nuovo test mirato; evitare modifiche non necessarie alla configurazione Spring Security.

### M1-02 — Utenti, ruoli e autorizzazioni

- Creazione degli utenti da parte dell'amministratore.
- Password temporanea e obbligo di cambiarla al primo accesso.
- Ruoli multipli e permessi granulari (RBAC), con eventuali override allow/deny.
- Reset/forzatura del cambio password e disabilitazione utenti; invalidazione delle sessioni secondo le regole di sicurezza previste.
- **Nessuna autenticazione a due fattori prevista in questa fase.**
- L'utente ha verificato creazione utenti, cambio password obbligatorio, restrizioni di accesso e disabilitazione.

### M1-03 — Interfaccia

- Dashboard e navigazione laterale.
- Interfaccia responsive, pagina profilo, gestione amministrativa e menu coerenti con i permessi.
- Componenti e gestione degli errori riutilizzabili.
- Dashboard predisposta per i dati di clienti e prodotti.
- L'utente ha confermato il funzionamento.

### M1-04 — Clienti

- Anagrafica di clienti **aziendali e privati**.
- Ragione sociale/nome, partita IVA, codice fiscale, indirizzi, recapiti e note.
- Contatti multipli per cliente.
- Ricerca, paginazione, creazione, modifica, archiviazione e ripristino.
- Permessi `CUSTOMERS_READ` e `CUSTOMERS_WRITE`.
- Migrazione prevista/applicata: **Flyway V102**.
- L'utente ha verificato tutte le principali operazioni.

### M1-05 — Prodotti installati

- Registrazione di prodotti hardware, software e servizi associati a un cliente.
- Produttore, modello, matricola/codice licenza, date di installazione e scadenze di garanzia/licenza.
- Stati, tra cui attivo, in manutenzione e dismesso.
- Ricerca, modifica, paginazione e filtro per cliente.
- Permessi `PRODUCTS_READ` e `PRODUCTS_WRITE`.
- Migrazione **Flyway V103**.
- Verifiche registrate: build React riuscita, compilazione e test backend completati, Docker Compose operativo, schema Flyway alla versione 103 e health API `UP`.
- L'utente ha confermato che le operazioni nell'interfaccia funzionano.

**Chiarimento architetturale successivo:** l'attuale sezione “Prodotti installati” non equivale a un catalogo commerciale. È necessario introdurre il catalogo e collegare le installazioni secondo le regole della sezione 5.

## 5. Decisione centrale: catalogo vs prodotti installati

### Catalogo prodotti

È l'anagrafica dei prodotti e delle offerte dell'azienda, indipendente dai clienti. Un elemento di catalogo può comprendere:

- Codice, nome, descrizione e tipologia: hardware, software, servizio o formazione.
- Produttore, modello e stato commerciale.
- Prezzo ed eventuali dati economici.
- Indicazione se il prodotto è **nostro** o **di terze parti**.

Un prodotto si definisce una volta nel catalogo e può essere associato a più clienti.

### Prodotti installati / attivati presso i clienti

Rappresentano una specifica istanza o assegnazione a un cliente, con dati operativi quali:

- Cliente di riferimento.
- Matricola, licenza, quantità o utenti attivati, secondo la tipologia.
- Date di installazione/attivazione, scadenza della licenza e garanzia.
- Stato operativo, informazioni di assistenza e dismissione.

Esempi: un PC Dell identificato dalla matricola presso il Cliente A; dieci licenze Microsoft 365 attivate per il Cliente B; una specifica installazione di software sviluppato internamente presso il Cliente C.

**Non tutti gli elementi del catalogo generano un'installazione:** formazione e consulenza sono tipicamente servizi erogati, non beni installati. La loro futura gestione operativa va modellata separatamente.

### Regole vincolanti sull'origine

| Origine dell'installazione | Collegamento al catalogo | Inserimento diretto |
| --- | --- | --- |
| `INTERNAL` — prodotto nostro | **Obbligatorio** | Non consentito senza voce di catalogo |
| `THIRD_PARTY` — prodotto esterno | **Facoltativo** | Consentito |

- Un **prodotto nostro** deve sempre derivare da una voce del catalogo Penny.
- Un **prodotto di terze parti** può essere già presente presso il cliente e censito direttamente, anche se non viene venduto dall'azienda.
- Un prodotto di terze parti **rivenduto abitualmente** può essere inserito nel catalogo e collegato alle installazioni.
- **Produttore e fornitore sono concetti distinti**: ad esempio, hardware prodotto da Dell, acquistato da un distributore e installato dalla nostra azienda.
- L'origine (`INTERNAL`/`THIRD_PARTY`) deve essere verificata dal backend, non solo dall'interfaccia.

### Esempi

1. **Software nostro:** `Penny Gestionale` è presente nel catalogo; l'installazione presso Azienda Rossi deve riferirsi obbligatoriamente a quella voce.
2. **Hardware di terzi già posseduto dal cliente:** un server Dell viene censito direttamente tra i prodotti installati del cliente, senza inserimento obbligatorio nel catalogo.
3. **Hardware di terzi rivenduto:** un firewall Fortinet può essere inserito nel catalogo; la relativa installazione presso il cliente ne richiama la scheda.

## 6. M1-06 — Catalogo prodotti e collegamento alle installazioni

**Stato: specifiche concordate, implementazione non verificata in questa conversazione.**

### Ambito concordato

1. Introdurre l'anagrafica centralizzata del catalogo prodotti.
2. Gestire nome, codice, tipologia, produttore, descrizione, prezzo e stato.
3. Distinguere prodotti interni e prodotti di terze parti.
4. Rendere obbligatorio il riferimento al catalogo per le installazioni `INTERNAL`.
5. Consentire il riferimento facoltativo al catalogo per le installazioni `THIRD_PARTY`, con inserimento diretto dei dati identificativi.
6. Distinguere produttore e fornitore.
7. Integrare la selezione dal catalogo nella creazione/modifica di un prodotto installato.
8. Aggiungere la sezione **Catalogo prodotti** alla navigazione.
9. Introdurre i permessi `CATALOG_READ` e `CATALOG_WRITE`, integrati nell'RBAC.
10. Conservare i prodotti installati già esistenti, senza perdita di dati.
11. Aggiungere test automatici per i vincoli sulle origini e le autorizzazioni.

### Vincoli di implementazione

- Migrazione Flyway **incrementale** successiva a V103, da numerare verificando lo stato reale del repository.
- Non eliminare, ricreare o sovrascrivere dati preesistenti.
- Progettare una strategia esplicita per classificare i prodotti installati esistenti che oggi non hanno origine o collegamento al catalogo; **non attribuire automaticamente `INTERNAL` senza evidenze**.
- Validare i vincoli nel backend e nell'interfaccia.
- Preservare login, sessioni, CSRF e autorizzazioni già operative.
- Eseguire build e test prima di proporre il merge.
- Branch suggerito: `feature/m1-06-product-catalog`; **nessun merge automatico**.

## 7. Decisioni UX sulla paginazione

È stata osservata una disposizione poco utile dei pulsanti **«Precedente»** e **«Successiva»** in varie schermate.

Comportamento concordato:

- Se i risultati occupano una sola pagina, **nascondere i pulsanti** e mostrare soltanto il conteggio, ad esempio `1 prodotto`.
- Se esistono più pagine, mostrare i controlli in basso, affiancati, con un indicatore come `Pagina 1 di 3`.
- Separare il conteggio dei risultati dai comandi di navigazione.
- Applicare una soluzione coerente a clienti e prodotti installati; uniformare anche le altre liste quando opportuno.

È stato fornito il pacchetto `penny-ui-pagination-fix.zip`, ma **non risulta una conferma esplicita dell'esito della sua installazione**. Verificare che la correzione sia realmente presente nel repository.

## 8. Problemi tecnici osservati e verifiche

- Durante M1-04 è comparso un errore di avvio relativo al bean Spring `PasswordEncoder` richiesto da `AdminController`. Il bean BCrypt era effettivamente dichiarato in `SecurityConfig.java`; dopo la ricreazione pulita del container backend, l'endpoint health ha restituito `HTTP 200`. **La causa esatta non è stata dimostrata**, quindi non aggiungere un secondo bean alla cieca.
- Durante M1-05 l'avvio backend è risultato regolare, senza ricomparsa di quell'errore.
- Per M1-05 il log ha confermato `Current version of schema "public": 103`, validazione di cinque migrazioni e health `{"status":"UP"}`.
- Gli avvisi relativi a Mockito/Byte Buddy comparsi nei test non sono stati segnalati come errori bloccanti.
- Resta da verificare la discrepanza `401`/`403` del login senza CSRF menzionata in M1-01.

## 9. Attività successive e questioni aperte

### Prima di iniziare M1-06

- Verificare il branch principale e sincronizzare le modifiche locali già applicate al Mac.
- Confermare che la patch di paginazione sia stata installata e versionata.
- Esaminare schema e API effettive dei prodotti installati, per definire una migrazione compatibile con i dati esistenti.
- Verificare il comportamento corrente dei permessi e dei menu prima di introdurre `CATALOG_*`.

### Dopo M1-06

È stata discussa una successiva **M2 dedicata ai processi operativi del CRM**. Sono emersi come possibili ambiti: preventivi, ordini, contratti, rinnovi, interventi tecnici e assistenza. **Non è stata ancora concordata in questa conversazione una specifica dettagliata o una sequenza definitiva delle milestone M2.**

## 10. Principi da mantenere

1. Nessuna perdita di dati nelle migrazioni.
2. Autorizzazioni verificate lato server; menu e controlli UI coerenti con i permessi.
3. Distinzione netta fra catalogo commerciale, installazioni/attivazioni e servizi erogati.
4. Catalogo obbligatorio per i prodotti interni installati; facoltativo per quelli di terze parti.
5. Produttore distinto dal fornitore.
6. Modifiche piccole, verificabili e versionate; test prima del merge.
7. Non dichiarare una milestone completata senza conferme tecniche e funzionali.

---

*Questo documento sintetizza decisioni e risultati dichiarati nella chat. Le implementazioni effettive devono essere controllate nel repository prima di procedere con nuove modifiche.*
