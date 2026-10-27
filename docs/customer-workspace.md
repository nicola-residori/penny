# Scheda cliente, sedi e calendario

La scheda cliente riunisce Panoramica, Anagrafica, Contatti, Sedi, Software, Hardware e Scadenze. Le sezioni sui prodotti rispettano PRODUCTS_READ. Sedi e dati cliente rispettano CUSTOMERS_READ/WRITE. Le prime 100 installazioni compaiono in panoramica, con totale e rimando agli elenchi completi paginati.

Software e Hardware consentono l'inserimento del pregresso senza creare una vendita o allocazione. INTERNAL mantiene l'obbligo del catalogo; THIRD_PARTY permette prodotti liberi. Le etichette distinguono l'origine anche senza colore. La sede si assegna dalla tabella di panoramica; deve appartenere allo stesso cliente.

Hardware contiene Dispositivi e Gruppi hardware, conservando la gestione delle precedenti postazioni e tutti i collegamenti. Nomi tecnici delle API/tabelle e permessi WORKSTATIONS restano compatibili.

Il calendario mensile mostra le garanzie e le scadenze licenza registrate sui prodotti. Non genera scadenze contrattuali inesistenti. Richiede PRODUCTS_READ e CUSTOMERS_READ. La scheda cliente mostra lo scadenzario del singolo cliente.

V107 aggiunge il permesso amministrativo TRASH_PURGE. V108 aggiunge sedi e collegamenti nullable senza trasformare o rimuovere i dati esistenti. Le sedi seguono il cliente nel ripristino/cancellazione definitiva; i vincoli impediscono riferimenti a clienti eliminati o sedi di altri clienti.
