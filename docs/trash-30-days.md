# Cestino —30 giorni

## Comportamento
Archivio e cestino sono funzioni distinte. Archivia conserva l'elemento senza scadenza. Elimina sposta nel cestino e nasconde dagli elenchi normali, dalle ricerche e dai selettori. Nessun vecchio archivio viene convertito in cancellazione.
Entro720ore dalla cancellazione l'utente autorizzato può ripristinare lo stato precedente, incluso attivo/archiviato. Alla scadenza il ripristino non è più disponibile. La pulizia elimina definitivamente i record scaduti ogni ora; riparte60secondi dopo l'avvio e recupera le scadenze maturate mentre l'applicazione era spenta. Il pulsante «Svuota cestino» consente agli utenti autorizzati di rimuovere definitivamente gli elementi prima della scadenza, dopo conferma esplicita e irreversibile.

## Entità e collegamenti
Clienti, contatti, catalogo, prodotti installati e postazioni hanno il cestino. Vendite e account non sono inclusi: le vendite mantengono annullamento e storico.
La cancellazione viene bloccata per clienti con vendite/installazioni/postazioni, voci catalogo con vendite/installazioni, postazioni con installazioni e installazioni con assegnazioni. I collegamenti bloccano anche quando il record figlio è nel cestino, fino alla sua rimozione definitiva: nessuna cascata sullo storico commerciale. L'archiviazione resta disponibile.
I contatti appartengono al cliente: un cliente senza altre dipendenze può essere eliminato con i contatti inclusi. I contatti rimangono nascosti col cliente, ricompaiono se ripristinato e vengono rimossi definitivamente con esso alla scadenza. Un contatto eliminato singolarmente ha la propria voce e scadenza; non può essere ripristinato se il cliente è nel cestino.
Gli indici univoci restano occupati per30giorni: per riusare un'identità ancora presente occorre ripristinarla oppure attendere la rimozione definitiva. Nessuna perdita di ripristinabilità dovuta a duplicati creati nel frattempo.

## Autorizzazioni
TRASH_READ,TRASH_DELETE,TRASH_RESTORE sono inizialmente concessi ad ADMIN,SALES,TECH, ma ogni record richiede anche i permessi originali del modulo. I tecnici, ad esempio, non eliminano né ripristinano clienti perché non hanno CUSTOMERS_WRITE. TRASH_PURGE è concesso inizialmente solo ad ADMIN. Lo svuotamento richiede anche TRASH_READ e READ/WRITE del modulo originale di ogni elemento. Override ALLOW/DENY invariati. Il cestino mostra solo le tipologie leggibili e non restituisce prezzi, chiavi licenza o password.

## API
GET `/api/trash?q=&page=0&size=20` (massimo100): id della voce, tipo/ID originale, etichetta, eliminato/scadenza, giorni rimanenti, stato scaduto, motivo di eventuale blocco e canRestore. Restituisce inoltre canEmpty e purgeableTotal: quest’ultimo conta tutti gli elementi autorizzati allo svuotamento, indipendentemente da ricerca e pagina; eventuali vincoli vengono verificati durante la rimozione.
DELETE `/api/trash/{entityType}/{entityId}` sposta nel cestino. Tipi: CUSTOMER,CONTACT,CATALOG_PRODUCT,INSTALLED_PRODUCT,WORKSTATION.
POST `/api/trash/{entryId}/restore` ripristina; dopo scadenza risponde410. Collegamenti incompatibili rispondono409. Il vecchio endpoint DELETE dei contatti ora usa il cestino invece della rimozione immediata.
POST `/api/trash/empty` con JSON `{ "confirm": true }` svuota tutte le voci autorizzate catturate all’inizio della richiesta, senza attendere scadenza o intervallo di ritentativo. Risponde con purged, blocked e skipped; le voci bloccate o occupate da altre operazioni restano conservate. Non include nuove voci aggiunte dopo la cattura. Una conferma assente o falsa risponde400.
Profilo: canUseTrash,canDeleteRecords,canRestoreTrash; i controlli server non si affidano ai pulsanti visibili.

## Integrità, audit e pulizia
Migrazione V107 additiva: registra TRASH_PURGE e lo concede al solo ruolo ADMIN, senza modificare dati o svuotare il cestino. Migrazione V106 additiva; V1–105 restano immutate. Aggiunge deleted_at su5tabelle e un registro cestino con scadenza fissa720ore, autore, etichetta e stato pulizia. I vecchi dati non entrano nel cestino automaticamente.
I trigger impediscono modifiche a elementi eliminati, nuovi collegamenti verso genitori eliminati e spostamenti incompatibili con le FK. Record e registro cambiano nella stessa transazione. Ripristino e pulizia bloccano la stessa voce prima di agire sul record.
Pulizia programmata e svuotamento manuale condividono la stessa procedura di rimozione, con blocco della voce e del record, vincoli e audit. Lo svuotamento manuale registra come autore l’utente che lo ha richiesto. Ogni rimozione definitiva avviene in una transazione indipendente. Un vincolo inatteso conserva record e voce, scrive un motivo generico di blocco e ritenta alla successiva pulizia: il fallimento di un elemento non impedisce di pulire gli altri. Il sistema non forza la rimozione di collegamenti.
Audit transazionale TRASH_MOVED,TRASH_RESTORED,TRASH_PURGED in penny_m2_audit, senza copie dei contenuti eliminati. La cancellazione dall'applicazione non elimina eventuali backup preesistenti, che mantengono la propria politica di conservazione.
Configurazione: `penny.trash.cleanup-enabled` defaulttrue; `penny.trash.cleanup-interval-ms` default3600000; `penny.trash.cleanup-initial-delay-ms` default60000. Nei test di cutoff la pulizia è invocata esplicitamente; un test separato verifica l'esecuzione realmente pianificata con un intervallo accelerato.

## Verifica e rilascio
`bash scripts/verify-trash.sh` usa copia e PostgreSQL temporanei, fuori dai mount attivi. Verifica cutoff, ripristino, permessi, controller ordinari, concorrenza, purge parziale, scheduler e migrazione105→106. UI coperta da test su conferme, annullamento, errori, scadenza, autorizzazioni, navigazione e paginazione dopo cancellazione.
Prima dell'attivazione vengono salvati sorgenti e backup PostgreSQL completo; dopo l'avvio si controllano versione106, health e confronto di tutte le righe precedenti. Branch feature/trash-retention-30-days; nessun commit,push o merge automatico.
