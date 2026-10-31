# Dashboard: andamento commerciale

In Panoramica, sotto il riepilogo generale di tutti i periodi, la sezione **Andamento commerciale** mostra vendite e clienti nel tempo. Il riepilogo superiore resta indipendente dai filtri; i nuovi indicatori e grafici si riferiscono al periodo applicato.

1. Impostare **Dal**, **Al**, lo **Span**: ad esempio `1d`, `2d`, `1w`, `2w`, `1mon`, `3mon`, `1y` o `2y`. Il campo propone preset ma accetta anche multipli personalizzati.
2. Scegliere **Tutti i clienti** oppure **Singolo cliente**; per quest'ultimo cercare il nome e selezionarlo. Il selettore **Elenco clienti** consente di consultare anche gli archiviati.
3. Premere **Applica intervallo**. Le scorciatoie Anno corrente, Ultimi 12 mesi, Ultimi 5 anni preparano i filtri, da applicare esplicitamente.

Le unità sono `d` giorni, `w` settimane, `mon` mesi e `y` anni. `m` (minuti) e `h` non sono supportati: le vendite hanno una data, senza orario. Le settimane iniziano di lunedì; i multipli hanno confini fissi, indipendenti dal filtro. La tabella e i tooltip indicano inizio e fine di ogni intervallo. I periodi ai bordi contano soltanto le date comprese nel filtro.

Tre grafici: valore vendite EUR; risorse vendute; clienti acquisiti cumulativi. Passare sui punti o selezionarli da tastiera per leggere i valori esatti. La tabella **Dati dei grafici** espone gli stessi dati; grafici lunghi sono scorrevoli orizzontalmente anche su mobile. I mesi/anni senza vendite sono mostrati a zero.

## Come sono calcolati

- Gli importi sono la somma di quantità × prezzo unitario delle vendite **confermate**, per `sold_on`. Non sono incassi né fatturato contabile; IVA o movimenti finanziari non sono modellati in questo modulo.
- Le risorse sono la somma delle quantità vendute, su tutte le categorie commerciali; non il conteggio di installazioni. Il numero vendite conta le singole righe registrate. Le allocazioni non moltiplicano questi valori.
- Nuovo cliente = prima data vendita storica di una anagrafica con stato Cliente. Le vendite poi annullate continuano a testimoniare l'acquisizione, coerentemente con la mancata retrocessione a Prospect. La curva cumulativa comprende anche i clienti acquisiti prima dell'inizio dell'intervallo; non è una ricostruzione delle sole anagrafiche attive in ogni anno.
- Clienti legacy senza vendite hanno data ignota: il conteggio è indicato separatamente e non si inventa una data. La quantità di anagrafiche attive Clienti e prospect del riepilogo generale ha quindi una definizione differente.
- Storico su dati ancora conservati nel CRM, anche archiviati/cestinati. La cancellazione definitiva di dati storici non può essere ricostruita. Il filtro singolo cliente usa anagrafiche disponibili, comprese le archiviate.
- Date inclusive; primo/ultimo periodo contano soltanto i giorni compresi nel filtro. Massimo 500 periodi per span personalizzato tra il 1900 e il 2100. Se si supera il limite, scegliere uno span più ampio o un intervallo più breve. Massimi moltiplicatori: 9999d, 9999w, 2400mon, 200y.

## Permessi e verifiche

SALES_READ espone importi, quantità e numero vendite; CUSTOMERS_READ espone acquisizione clienti e permette il filtro cliente. Senza permesso, le relative metriche sono assenti dall'interfaccia e null nell'API. Non sono dedotte dal semplice accesso all'inventario.

API `GET /api/analytics?from=YYYY-MM-DD&to=YYYY-MM-DD&span=2d&customerId=opzionale`, lettura in transazione con snapshot coerente, importi restituiti come stringhe decimali esatte. Nessuna migrazione o modifica dei dati. La demo v3 contiene circa due anni di vendite giornaliere: selezionare Anno corrente o Ultimi 12 mesi e attivare il confronto per provare gli scostamenti.

Compatibilità API: le richieste precedenti `groupBy=YEAR|MONTH` continuano a funzionare, mantenendo il limite precedente di 201 anni o 120 mesi. Se presente, `span` prevale. La risposta restituisce lo span canonico e `periodEnd` incluso per ogni bucket. Per stabilità i giorni sono allineati al 01/01/1970, le settimane al lunedì 05/01/1970, mesi e anni all'inizio del 1970; questo vale anche per date precedenti al 1970.

## Confronto con l’anno precedente

Lo switch **Confronta con l’anno precedente** è spento all’apertura. Attivandolo, senza riapplicare i filtri, tutti e tre i grafici aggiungono la linea tratteggiata dell’anno precedente sulla stessa scala. Passare su un punto o selezionarlo da tastiera mostra valore attuale, valore precedente, differenza assoluta e percentuale. La tabella include gli stessi scostamenti.

Il confronto usa il periodo effettivamente selezionato, spostato di un anno civile, anche per span multipli e per il singolo cliente. I mesi interi confrontano mesi interi, compreso febbraio bisestile; il singolo 29 febbraio non ha equivalente e rimane senza confronto. Con valore precedente pari a zero lo scostamento assoluto è disponibile, ma la percentuale è non calcolabile. Nessuna percentuale viene inventata.

L’API accetta `comparePreviousYear=true` ed estende ogni punto con `comparison`: intervallo precedente, metriche precedenti, gap esatti e percentuali. Restano applicati i permessi delle rispettive metriche. Lo switch spento evita le query aggiuntive.

Il dataset demo v3 aggiunge 48 clienti con acquisizioni distribuite su 24 mesi, vendite giornaliere stagionali, contratti, corsi, sviluppi e attività tecniche con interventi. Il caricamento è incrementale e idempotente. In **Amministrazione → Ambiente e dati dimostrativi → Azzera dati CRM**, la conferma `RESET` elimina tutti i dati aziendali (anche quelli non demo), conservando utenti, ruoli e permessi e le configurazioni di base. Il reset non viene mai eseguito durante il caricamento o il rilascio.
