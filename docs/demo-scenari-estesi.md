# Demo estesa E2

Il pulsante di caricamento completa la demo esistente senza azzerarla. I nuovi dati hanno prefisso `[DEMO] E2`, i codici commerciali e le matricole iniziano con `E2-`. Il caricamento successivo non duplica i dati. Non vengono creati utenti.

Il solo ampliamento E2 aggiunge 50 anagrafiche (10 privati, di cui 2 Clienti e 8 Prospect), 171 sedi, 80 identità di contatto, 40 voci di catalogo, 178 installazioni, 120 vendite, 32 assegnazioni, 31 gruppi e 6 elementi nel cestino. Trenta anagrafiche diventano Clienti attraverso le vendite; venti restano Prospect (due nel cestino). Le 120 vendite comprendono 92 confermate e 28 annullate. I dati demo originali restano presenti e si sommano a questi numeri.

Casi di prova:

- `[DEMO] E2 Gruppo Atlas`: cinque sedi e 110 installazioni per provare seconda pagina e filtri del riepilogo.
- Aziende E2 da 02 a 40 e privati E2 da 41 a 48: sedi multiple, recapiti, clienti convertiti da vendite e prospect senza vendite.
- `[DEMO] E2 Referente condiviso 01`–`20`: identità condivise con ruoli e principale diversi per associazione, sedi diverse e associazioni senza sede.
- Catalogo `E2-HW-*`, `E2-SW-*`, `E2-SVC-*`, `E2-TRN-*`: quattro tipi, origini interna/terzi, prezzi assenti/zero/noti, prodotti archiviati con riferimenti storici.
- Vendite `E2-ORD-*`: confermate disponibili, parzialmente assegnate, completamente assegnate e annullate con motivo. La formazione resta solo commerciale.
- Installazioni: software e hardware con catalogo; pregresso di terzi senza catalogo; quantità e utenti assenti/noti; produttore distinto dal fornitore; stati attivo, manutenzione e dismesso; scadenze passate, imminenti e future.
- Cestino E2: cliente autonomo, cliente con contatto condiviso, contatto, catalogo inutilizzato, installazione senza assegnazioni, gruppo vuoto. Permanenza residua 7, 15 o 30 giorni. Il cliente con contatto condiviso è ripristinabile ma la rimozione definitiva resta bloccata finché non si rimuove l'associazione condivisa; il contatto resta visibile sull'altra anagrafica.

Gli esempi riguardano le funzioni attuali. Non simulano contratti, assistenza o erogazione corsi futuri.

## Controlli rapidi

- Cercare `E2 Gruppo Atlas` nelle anagrafiche, aprire riepilogo/installazioni e passare oltre i primi 100 risultati.
- Cercare `E2 Referente condiviso 01`: è collegato ad Atlas, Azienda 12 e al Cliente condiviso nel cestino. Cambiare i recapiti verifica l'identità condivisa; ruolo e sede restano specifici dell'associazione.
- `E2 Azienda 02`: ordini parzialmente assegnati; `E2 Azienda 03`: ordini disponibili senza assegnazioni; `E2 Azienda 04`: quattro ordini annullati, stato Cliente conservato dopo la prima vendita.
- `E2 Azienda 31`–`33`: servizi legacy senza vendita. `E2 Azienda 35`–`40`: pregresso hardware/software senza vendita. Queste anagrafiche restano Prospect.
- `E2 Azienda 30` e `E2 Privato 48`: anagrafiche archiviate, con stato commerciale indipendente.
- Catalogo `E2-TRN-10`: voce archiviata inutilizzata nel cestino, ripristinabile.
- I cestini `Cliente autonomo` e `Installazione` hanno circa 7 giorni residui; `Cliente condiviso` e catalogo circa 15; `Contatto 80` e `Gruppo vuoto` circa 30.

I privati `[DEMO] E2 Privato 05` e `[DEMO] E2 Privato 15` hanno vendite reali demo e stato Cliente; i privati 41–48 restano Prospect. Le installazioni possono essere senza gruppo: quando il gruppo è presente, la sede coincide con quella del gruppo.

## Prove delle regole

Partire dai dati validi e tentare queste operazioni dall'interfaccia:

- Registrare la prima vendita su un Prospect senza vendite: diventa Cliente automaticamente. Il solo censimento di un prodotto pregresso mantiene Prospect.
- Collegare un contatto condiviso a una sede diversa per ciascuna anagrafica; lasciare la sede vuota su un'associazione. Rimuovere l'ultimo collegamento deve essere impedito.
- Provare un prodotto nostro senza catalogo e una sede appartenente a un altro cliente: il salvataggio deve essere rifiutato. Per un prodotto di terzi il catalogo può mancare.
- Provare un'assegnazione oltre il disponibile, una vendita annullata o un'installazione dismessa: operazione rifiutata. Annullare una vendita confermata non riporta il Cliente a Prospect.
- Ripristinare un elemento dal cestino; confrontare archiviazione e cancellazione recuperabile. Lo svuotamento rispetta i vincoli sui riferimenti condivisi.
- In Amministrazione, ripetere il caricamento non duplica nulla. Il reset richiede la parola RESET ed elimina tutti i dati CRM, anche quelli non demo: usarlo solo quando si vuole un ambiente vuoto. Non viene eseguito dal caricamento.

Non vengono memorizzati dati intenzionalmente incoerenti per simulare gli errori.
