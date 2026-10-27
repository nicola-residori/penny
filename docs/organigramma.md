# Organigramma di aziende, sedi e contatti

Nella scheda cliente/prospect aprire **Organigramma**. L'azienda è il nodo principale; ogni sede è un ramo espandibile. Cliccare una sede apre indirizzo e contatti; **Mostra/Nascondi contatti** permette una lettura compatta. I contatti senza sede hanno un ramo separato: non viene inventata una sede per loro.

Cliccare un contatto apre la mappa delle sue associazioni: cliente/prospect, sede facoltativa, ruolo e indicazione Principale specifici di quell'associazione. Cliccare un'azienda o la sede associata cambia il centro della mappa. **Indietro nella mappa** torna al nodo precedente. È possibile aprire questa vista anche dal pulsante con icona **organigramma inverso** di ogni scheda contatto, con permessi di sola lettura.

In **Sedi → Nuova sede / Modifica sede**, selezionare una o più tipologie: Legale (viola), Amministrativa (blu), Operativa (verde), Commerciale (ambra), Magazzino (arancio). I badge compaiono nella mappa, nell'elenco sedi e nel riepilogo. Nessuna selezione indica **Non classificata**, senza deduzioni dal nome. I ruoli restano testo libero per ciascun cliente e vengono visualizzati come badge di colore stabile; non definiscono permessi applicativi.

Le associazioni a clienti nel cestino e i contatti eliminati sono esclusi dalla mappa; gli archiviati restano riconoscibili con badge. Non cambiano le regole sui contatti condivisi: almeno un cliente/prospect, una sede facoltativa per associazione.

Per provare: cercare **E2 Gruppo Atlas** (cinque sedi) e aprire la mappa; **E2 Referente condiviso 01** collega Atlas e altre anagrafiche con ruoli diversi. Le sedi demo E2 hanno esempi di doppia tipologia. Le sedi reali preesistenti restano non classificate finché non si modificano esplicitamente.

Backend: Flyway V114 aggiunge `site_types` JSONB con default vuoto e vincoli per codici ammessi/unici. PUT omesso conserva le tipologie; array vuoto le rimuove; null, duplicati e codici sconosciuti sono rifiutati. API `GET /api/customer-organization/{id}` e dettaglio contatto richiedono CUSTOMERS_READ, modifiche sedi CUSTOMERS_READ e CUSTOMERS_WRITE. Nessun dato tecnico o credenziale dei prodotti nella mappa.

Le azioni organigramma sono senza testo visibile: sulla scheda cliente l'icona ha un nodo sopra e due sotto; sul contatto è invertita. I tooltip descrivono l'azione al passaggio del mouse o al focus da tastiera e sono chiudibili con Esc. Il nome accessibile del tab resta Organigramma e la navigazione con frecce resta disponibile.
