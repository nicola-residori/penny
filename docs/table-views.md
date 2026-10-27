# Elenchi, filtri ed Excel

Le liste di clienti, contatti, catalogo, software, hardware, formazione, contratti, sviluppi, attività e vendite utilizzano tabelle ordinabili. Il nome e la riga aprono il dettaglio; i controlli nella riga mantengono la propria azione.

I campi sotto ogni intestazione filtrano per contenuto e si combinano tra loro. Il caricamento comprende tutte le pagine autorizzate dal server: i filtri e **Esporta Excel** operano sull'intero risultato, non sulla sola pagina visibile. L'esportazione è disabilitata durante il caricamento e in caso di errori o dati incompleti. Il file `.xlsx` include le colonne visibili, l'ordinamento corrente e tutte le righe filtrate, con intestazione bloccata e filtri Excel. I testi sono celle testuali, anche quando iniziano con `=`. Gli importi che superano la precisione di Excel rimangono testo per conservarne le cifre.

Nell'elenco clienti, indirizzo e provincia operativi provengono dalle sedi contrassegnate **Operativa**. Più sedi sono mostrate insieme. La provincia si compila nell'editor della sede: la migrazione V119 aggiunge un campo facoltativo senza ricavarlo dall'indirizzo legale o modificare dati storici. All'interno del cliente rimangono la vista rapida a sinistra e i box aziendali, commerciali e amministrativi.

Per i nuovi contratti è obbligatoria la tipologia prodotto; il nome è facoltativo e, se vuoto, viene registrato con il nome della tipologia. I contratti storici privi di tipologia restano modificabili. Il campo **Tipo contratto** (generale, telefonico, aggiornamenti, orario) rimane distinto dalla tipologia prodotto. Il **Canale di vendita** è esplicito e facoltativo; non viene dedotto dal costo del collaboratore. Le scadenze delle schede cliente non vengono etichettate automaticamente come terze parti.

Restano applicati i permessi di lettura e scrittura dei singoli moduli e degli importi. Il catalogo mantiene solo il canale di vendita nell'interfaccia; le postazioni restano un riferimento manuale facoltativo del prodotto.
