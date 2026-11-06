# Tele-assistenza

Disponibile nella navigazione principale e nella scheda cliente. La Panoramica cliente mostra anche il grafico e il pulsante di registrazione diretta, con cliente preimpostato. La registrazione crea un solo intervento `INTERVENTION`, classificato `REMOTE_SUPPORT`, con richiedente e tecnico. Il contatto deve appartenere al cliente; l'inserimento inline crea e associa il contatto nella stessa transazione della chiamata. Gli indirizzi email e le sedi del contatto restano facoltativi.

Gli utenti devono avere lettura clienti e lettura/scrittura attività. Creare un nuovo contatto richiede anche scrittura clienti. Il tecnico selezionato deve essere attivo e abilitato a leggere clienti e gestire attività. La selezione viene registrata immediatamente: non esiste uno stato “senza tecnico”.

Apertura: timestamp automatico server, stato Avviata. Chiusura: Risolta, Parzialmente risolta o Non risolta. L'apertura del form di chiusura propone l'ora server corrente, modificabile; la fine non può precedere l'apertura e tollera al massimo due minuti di disallineamento dell'orologio. I commenti con autore/data possono essere aggiunti anche dopo la chiusura. Il record generico è protetto dalle modifiche che aggirerebbero questi stati; nell'elenco Interventi il dettaglio indirizza alla gestione specifica.

Annullamento definitivo, in ogni stato, con conferma UI. Rimuove intervento, chiamata, commenti, ore e collegamenti. Conserva le email originali, i relativi commenti mail e i documenti, scollegandoli dall'intervento rimosso. Rimane il solo evento tecnico di audit, privo di note e tempi della chiamata. Non passa dal cestino. Il reset amministrativo include le nuove tabelle.

Grafico mensile per mese di apertura e stato attuale, anno corrente predefinito e anni precedenti selezionabili. Colori: blu avviata, verde risolta, ambra parziale, rosso non risolta, sempre accompagnati da etichette testuali. Totali ore delle chiamate chiuse per cliente, tecnico e richiedente; tempo ripartito fra anni in base alla durata effettiva, con confini calendario Europe/Rome. Le chiamate aperte non alimentano ore concluse e restano nella fascia In corso, indipendentemente dalla giornata e dall'anno consultati. Eventuali variazioni di ora legale non alterano la durata effettiva.

Verifica: test PostgreSQL dei flussi/stati, contatti condivisi e creazione atomica, permessi, doppia chiusura, cancellazione aperta/chiusa, conservazione email originali, ripartizione tra anni e ora legale. Test UI di apertura da cliente/sezione generale, chiusura con modifica orario, conferma cancellazione. Test visuali su desktop/mobile e temi chiaro/scuro.

## Vista condivisa e notifiche

Ogni utente autenticato vede le chiamate aperte tramite il pulsante globale in testata o la sezione Tele-assistenza. Gli account senza lettura clienti/attività vedono soltanto cliente, richiedente, tecnico, riferimento e apertura; dettagli, commenti e storico conservano RBAC. Il feed operativo è paginato e ordinato dalle aperture più recenti.

La schermata completa mostra in alto le chiamate in corso con timer HH:mm:ss aggiornato ogni secondo e tecnico assegnato, anche se aperte nei giorni precedenti. Subito sotto compare l'elenco della giornata, con stato e ordine cronologico decrescente. La giornata è selezionabile; l'anno controlla solo i riepiloghi collocati più in basso. Elenco, bacheca e notifiche si aggiornano ogni 15 secondi quando la pagina è visibile, al focus e dopo operazioni locali.

All'assegnazione viene creata in transazione una notifica interna privata per il tecnico, anche per le chiamate già aperte al rilascio (V134). Campanella con conteggio e avviso in-app; nessun invio email o notifica del sistema operativo. La lettura è persistente e riservata al destinatario. La chiusura marca letta la notifica; l'annullamento la elimina tramite FK. Le notifiche non concedono accesso a dati o operazioni altrimenti non autorizzati.

## Dataset di prova tele-assistenza

Il file `backend/src/main/resources/demo/seed-support-demo.sql` aggiunge soltanto dati con marcatore `PENNY_SUPPORT_DEMO_V1`: 4 aziende `[DEMO ASSISTENZA]`, 8 sedi, 8 contatti (uno condiviso fra due aziende) e 43 chiamate. Dieci chiuse sono distribuite nella giornata di caricamento, sei nei giorni precedenti, ventiquattro nei mesi passati e tre restano aperte (una dal giorno precedente). Gli esiti chiusi sono 14 risolte, 13 parziali e 13 non risolte. I tecnici vengono scelti fra gli account esistenti attivi e abilitati, senza creare credenziali. Solo le tre chiamate aperte producono notifiche demo non lette.

Lo script è transazionale e ripetibile: non duplica i casi e non sovrascrive le modifiche fatte alle chiamate già caricate. Le nuove aziende sono prospect fino alla prima vendita, come previsto dal CRM. Il reset totale amministrativo comprende questi dati. Il test `SupportDemoSeedTest` verifica quantità, stati, associazioni, notifiche, ripetibilità e conservazione di un'anagrafica preesistente.

Sul server viene conservata una copia operativa in `/opt/penny/scripts/seed-support-demo.sql`. Il caricamento esplicito è preceduto da backup verificato e controllo delle impronte dei dati non demo. La presenza del file nelle risorse backend non lo esegue automaticamente all'avvio.
