# Allineamento alle note del 26 ottobre 2026

Fonte conservata in docs/sources/2026-10-26-business-notes.txt. Le funzionalità sono accessibili dai rispettivi moduli e dalla scheda cliente, con permessi dedicati.

| Area | Risultato |
|---|---|
| Anagrafica | Data creazione automatica; nome, PIVA, CF, settore, email/telefono generici e indirizzo; sito web, PEC, SDI, pagamento, due coordinate bancarie, note commerciali/amministrative. Referenti selezionabili dai contatti associati. |
| Stati azienda | Attivo, obsoleto, sostituito, cessata attività; precedente/successore con blocco cicli e protezione dei riferimenti dal cestino. Insolvente con banner e badge. |
| Stato commerciale | Prima vendita converte automaticamente Prospect in Cliente; Collaboratore è una qualifica aggiuntiva, non una conversione manuale. |
| Catalogo | Categorie e tipologie modificabili; tutte le voci iniziali richieste. Tipologie obsolete e categorie inattive restano nello storico e bloccano nuove vendite. Modello, unità, descrizione, fornitore predefinito/altro, canale e immagine facoltativa. |
| Origini | Origine prodotto nostro/terzi separata dal canale catalogo vendita interna/fornitura esterna e dalla provenienza della singola installazione vendita nostra/terzi. |
| Software | Tipologia, quantità licenze, chiave, release in uso e data modifica, release vendita, tipo licenza, scadenza disabilitata per perpetua, provenienza, data/prezzo manuali o derivati, postazione testo, tutti gli stati richiesti, note. Aggiornamento massivo per cliente/tipologia, anche selezioni su più pagine. |
| Hardware | Tipologia, modello, matricola, quantità, vendita/noleggio/conto visione e relativa scadenza, provenienza, data/prezzo, anni e scadenza garanzia calcolata, fornitore, costo, postazione, configurazione, stato in uso/obsoleto, note. |
| Formazione | Tipologia, corso, sede corso online/nostra/cliente/esterna, periodo e date, docente testo, partecipanti da contatti associati o nomi manuali, data/prezzo e note. |
| Contratti | Tipologia, generale/telefonico/aggiornamenti/orario, scadenza, sei stati, ore totali/residue decimali e vincoli, vendita/costo collaboratore, note. |
| Scheda cliente | Contatti, sedi, software, hardware, formazione, contratti, sviluppi, attività tecniche/commerciali; tutti alimentabili anche senza una vendita. Vista rapida a sinistra. |
| Navigazione | Liste globali alfabetiche con ordinamenti; rubrica Contatti generale e moduli separati Software, Hardware, Formazione, Contratti, Sviluppi e attività. Disponibilità tolta dal menu: i vecchi collegamenti rimangono gestibili nelle vendite come assegnazioni al parco, non magazzino. |
| Vendita e parco | Opzione Aggiungi al parco cliente: installazione e assegnazione oppure scheda formazione/contratto create nella stessa transazione, secondo categoria e permessi. Data e prezzo dei collegamenti sono autorevoli; più vendite non vengono appiattite su una sola data/prezzo. |
| Calendario | Licenze, garanzie, noleggi/conto visione, corsi, contratti e attività, rispettando i permessi dei moduli. |
| Sicurezza e cancellazioni | Importi effettivi riservati a SALES_READ; modifiche manuali a SALES_WRITE. Cestino, ripristino, svuotamento e reset estesi alle nuove schede. Reset conserva la configurazione delle categorie/tipologie. |

Le integrazioni indicate come future nel testo restano future: docenti selezionabili dagli utenti, ticket che scalano automaticamente le ore, gestione del magazzino e documenti di offerta. Prezzi di vendita nelle schede intesi unitari, distinti dal totale della riga vendita. I campi precedenti restano conservati; le nuove informazioni sconosciute non vengono inventate.
