# M2 — Vendite, disponibilità, postazioni e interfaccia

## Ambito e assunzioni
Implementazione autonoma richiesta dall'utente il 25 ottobre 2026. La roadmap disponibile definisceva M2 come catalogo, vendite aggregate e installato, senza il documento funzionale originale. Si prosegue da M1-06 con singole vendite manuali, totali derivati, allocazioni e postazioni; le scelte seguenti sono assunzioni esplicite, modificabili in milestone successive.

- Una vendita contiene un solo prodotto del catalogo per un cliente, data, quantità intera, prezzo unitario netto in EUR, riferimento e note. Per documenti con più prodotti si registrano più righe con lo stesso riferimento. Nessuna fattura fiscale né contabilità/IVA.
- Quantità 1–1000000; prezzo 0–999999999,99, massimo due decimali. Zero è ammesso se il prezzo non è disponibile. Backend BigDecimal e risposte monetarie come stringhe decimali; UI conserva i centesimi anche per totali elevati. Quantità e identificatori JSON frazionari vengono respinti.
- Le vendite confermate non si modificano: annullare con motivazione e registrare la correzione. Lo storico resta disponibile. Le quantità annullate non partecipano ai totali.
- I dati del prodotto venduto sono snapshot; modifiche successive al nome/codice catalogo non riscrivono lo storico. Origine/categoria di prodotti con vendite non possono essere cambiate, per preservarne l'identità.
- Nessuna vendita o postazione viene inferita dalle installazioni storiche. I dati esistenti restano invariati.

## Quantità e postazioni
Disponibile = venduto confermato − assegnato. La disponibilità è consultabile anche senza permessi sugli importi. Un'allocazione associa vendita e installazione dello stesso cliente/prodotto e ne assegna una quantità positiva. Non modifica il venduto. Si può aggiornare la quantità o liberare un'allocazione; i movimenti restano nell'audit.
Prima di annullare una vendita o dismettere un'installazione occorre liberare le allocazioni. Un prodotto di catalogo archiviato non accetta nuove allocazioni né aumenti, ma consente diminuzioni e rilascio di quelle esistenti.
Le postazioni appartengono a un cliente, con nome univoco nel cliente, ubicazione e note. Il collegamento all'installazione è facoltativo e deve restare nello stesso cliente. Una postazione referenziata non cambia cliente; archiviazione e riferimenti storici sono conservati. Un utente privo di lettura postazioni può mantenere il collegamento esistente, ma non cambiarlo o rimuoverlo.

## Permessi iniziali
| Permesso | ADMIN | SALES | TECH |
| --- | --- | --- | --- |
| SALES_READ / SALES_WRITE | sì | sì | no |
| INVENTORY_READ | sì | sì | sì |
| ALLOCATIONS_READ / ALLOCATIONS_WRITE | sì | sì | sì |
| WORKSTATIONS_READ / WORKSTATIONS_WRITE | sì | sì | sì |
Creare vendite richiede anche CUSTOMERS_READ e CATALOG_READ. Gestire allocazioni richiede INVENTORY_READ, PRODUCTS_READ, PRODUCTS_WRITE e ALLOCATIONS_WRITE. Consultarle richiede ALLOCATIONS_READ, INVENTORY_READ e PRODUCTS_READ. Override utente/ruolo restano invariati. Ruoli personalizzati non ricevono permessi automaticamente. I tecnici non ricevono prezzi o importi tramite inventario, allocazioni o dashboard.

## API
Contratto dettagliato: `docs/superpowers/specs/m2-api-contract.md`.
- `/api/sales`: elenco con ricerca, cliente, intervallo date, stato e paginazione; dettaglio, creazione e annullamento motivato.
- `/api/sales/summary`: riepiloghi per cliente/prodotto e totali globali dei filtri; solo confermate.
- `/api/inventory`: vendite confermate con quantità disponibili, senza campi monetari.
- `/api/allocations`: elenco, creazione, modifica quantità, rilascio.
- `/api/workstations`: elenco, dettaglio, creazione, modifica e archiviazione.
- `/api/overview`: contatori reali, null per le metriche non autorizzate.
- `/api/installed-products`: workstationId opzionale, nome della postazione solo se autorizzato.
Ogni elenco è paginato, size massimo100; selettori UI paginati e ricercabili. Date inclusive; intervalli invertiti respinti. Gli errori di concorrenza restituiscono409 con invito a ricaricare.

## Dati, audit e concorrenza
V105 è additiva; V1/V100–V104 non vengono modificate. Aggiunge vendite, allocazioni, postazioni, audit, workstation_id e una versione tecnica allocation_version sulle installazioni. Le FK composite impediscono associazioni tra clienti o prodotti diversi. Nessuna distruzione o backfill inventato.
I trigger aggiornano un contatore tecnico sia sulla vendita sia sull'installazione a ogni mutazione di allocazione. Questo serializza le assegnazioni e invalida snapshot obsoleti anche con transazioni PostgreSQL REPEATABLE READ. Le transazioni concorrenti incompatibili vengono respinte anziché produrre disponibilità negativa o installazioni dismesse con allocazioni.
Audit applicativo transazionale append-only in penny_m2_audit per vendite, annullamenti, allocazioni, postazioni e cambi di postazione sulle installazioni. Memorizza attore, evento, riferimenti e metadata limitati; non memorizza password o chiavi licenza. Non ricostruisce un audit retroattivo dei vecchi moduli.

## Interfaccia
Sidebar organizzata per lavoro, commerciale, assistenza e gestione, icone SVG coerenti, dashboard con dati reali e accessi rapidi. Nuova palette navy/teal, spaziatura e contrasto, feedback di caricamento/errore, focus tastiera, menu mobile e animazioni leggere. prefers-reduced-motion disabilita animazioni e transizioni. Le vendite usano il giorno locale dell'utente, anche attorno alla mezzanotte UTC.

## Verifica e attivazione
`bash scripts/verify-m2.sh` copia i sorgenti in una cartella temporanea ed esegue Maven, Vitest e build su un PostgreSQL dedicato. Non compilare i nuovi sorgenti nel mount del backend attivo durante lo sviluppo. La CI ha già PostgreSQL configurato per i test di integrazione.
Prima dell'attivazione: backup pg_dump completo, verifica dell'archivio, applicazione del delta sorgente sul branch feature/m2-sales-inventory-ux, riavvio e verifica V105/health/conservazione dati. Non eseguire Flyway clean né eliminare il volume del database applicativo. Nessun commit, push o merge automatico.

## Aree rinviate
M3–M6: opportunità/offerte, contratti/notifiche, progetti/Gantt, provvigioni, integrazioni ERP e release di produzione. Non sono state inventate le regole fiscali/provvigionali o lo scoping per agente. Restano le segnalazioni npm già presenti nelle dipendenze di sviluppo Vitest: il passaggio maggiore del test runner richiede una verifica separata.
