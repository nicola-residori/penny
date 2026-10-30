# Campi, ordini e margini

L’aggiornamento del 30 ottobre 2026 applica le specifiche di Campi.pdf e Vendite e Margini.pdf. Le migrazioni V123–V130 estendono lo schema esistente; le righe di vendita e le assegnazioni storiche mantengono i loro identificativi.

## Anagrafiche e parco cliente

Il primo ordine confermato converte automaticamente il prospect in cliente. È consentita anche la modifica manuale dello stato commerciale, come richiesto dal PDF. I contatti mantengono le associazioni condivise a clienti e sedi e aggiungono nome, cognome, recapiti aziendali/personali, stato e riferimento.

Il catalogo aggiunge gestione/profilo margini e garanzia. Software e hardware espongono i nuovi dati operativi, la provenienza, i riferimenti commerciali e la postazione facoltativa inserita manualmente. Restano consentiti censimenti senza vendita. I dati finanziari richiedono i rispettivi permessi; il parco di terze parti conserva la propria evidenza grafica.

## Ordini

Una testata comprende più righe, anche di categorie diverse. Il salvataggio crea una bozza con i calcoli dei margini; la conferma genera le registrazioni collegate nel parco cliente. Riconfermare non crea duplicati. Per l’hardware vengono create unità separate per consentire matricole distinte; le licenze possono essere completate successivamente.

Stato commerciale, informazioni operative mancanti e riconoscimento dei margini sono separati. Durate e garanzie vengono applicate solo quando è nota una decorrenza effettiva; non viene inventata una scadenza dalla data dell’ordine. I dati commerciali storici del catalogo non sostituiscono il contesto già acquisito dalla riga.

## Margini

Profili riutilizzabili definiscono le componenti; regole versionate stabiliscono condizioni, percentuale/importo fisso, segno, priorità e validità. I calcoli di ogni riga sono conservati in snapshot. Conferma, annullamento, rettifica e ricalcolo esplicito registrano eventi con utente e motivazione, mantenendo i valori originari. A parità di priorità prevale la regola creata più recentemente.

L’esempio numerico del PDF produce 50 + 150 + 120 − 30 = 290 euro, ossia il 29% di 1.000 euro. Il 23% riportato nell’immagine è un refuso.

## Attività e contratti

Corsi, contratti, sviluppi e attività espongono i campi specifici richiesti. Le attività tecniche e gli sviluppi possono contenere più task assegnati, avanzamento e Gantt, oltre a interventi collegati dello stesso cliente. Gli interventi ricevono numerazione annuale, per esempio 26.RI.001.

I contratti conservano rinnovi e rettifiche delle date. Un rinnovo futuro non interrompe la copertura del periodo corrente. L’avviso “Senza contratto” considera la copertura telefonica o oraria effettiva. Lo stato in scadenza si determina nei 20 giorni precedenti, quello scaduto dal giorno successivo; gli stati terminali manuali sono conservati.

## Liste, documenti e promemoria

Il menu Colonne consente di scegliere e riordinare i campi, anche quelli vuoti, mantenendo le preferenze nel browser. Filtri, ordinamento ed esportazione Excel usano i campi selezionati. Le liste caricano tutte le pagine necessarie, evitando il limite silenzioso ai primi 100 ordini.

Scelte predefinite permette all’amministratore di aggiungere o disattivare valori senza riscrivere lo storico. Documentazione gestisce allegati privati fino a 5 MB collegabili a cliente, ordine, prodotto o scheda operativa. Notifiche gestisce promemoria personali e avvisi sulle scadenze all’interno del CRM; non configura invii email o SMS.

Il reset amministrativo include le nuove tabelle commerciali e operative, preserva utenti, permessi e audit generale e continua a richiedere la conferma RESET. Non viene eseguito automaticamente durante l’aggiornamento.

## Verifica e installazione

Build e test vengono eseguiti esclusivamente nel CT Penny CRM, in un progetto Docker di test separato dalla produzione. L’installazione richiede backup verificato, controllo dei conteggi prima/dopo migrazione e controllo di salute dei servizi. Il lavoro resta sul branch codex/pdf-fields-sales-margins, senza merge automatico.

Verifica del rilascio: 170 test backend e 135 test frontend superati nel CT111; compilazione TypeScript e build Vite superate. I test includono riconferma idempotente, ordini misti, scadenze senza decorrenza, margini immutabili/rettifiche, rinnovi futuri, collegamenti allo stesso cliente, permessi, documenti privati e selezione/esportazione colonne.
