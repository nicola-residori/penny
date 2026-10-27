# Specifiche funzionali consolidate

Fonte: [riepilogo dell'utente](sources/README_PENNY.md), seguito dalle correzioni esplicite nella chat CRM. Il testo non ricostruisce domande o risposte non presenti nelle fonti.

## Catalogo e installato

Il catalogo comprende HARDWARE, SOFTWARE, SERVICE, TRAINING. Codice e nome obbligatori, origine INTERNAL/THIRD_PARTY, produttore, modello, descrizione, fornitore distinto e prezzo EUR opzionale non negativo. Prezzo assente significa non noto, non zero. Lo stato attivo/archiviato governa i nuovi collegamenti; i riferimenti storici sono conservati.

Un'installazione è associata al cliente, con categoria hardware/software/servizio legacy, produttore, fornitore, identificativi, date, stato ACTIVE/MAINTENANCE/RETIRED, quantità opzionale e utenti attivati opzionali. Le quantità note sono intere positive; utenti attivati è un intero non negativo. L'assenza di informazioni resta NULL anche nelle migrazioni e durante aggiornamenti con vecchi payload privi dei nuovi campi. Un null esplicito cancella il valore opzionale.

INTERNAL richiede una voce di catalogo dello stesso tipo e origine. THIRD_PARTY può essere censito direttamente o collegato al catalogo. Origine e provenienza commerciale non sono sinonimi: un Dell venduto da noi resta di terzi. Formazione non è installabile e non compare nelle disponibilità da assegnare alle installazioni, pur restando nelle vendite; consulenza e formazione avranno una gestione di erogazione separata. I record SERVICE già supportati sono preservati.

Nessuna installazione storica genera automaticamente vendite, assegnazioni o quantità vendute. Le scadenze attuali sono quelle di licenze e garanzie registrate sui prodotti.

## Clienti, prospect, sedi e contatti

Anagrafica azienda/privato, ricerca, paginazione, campi fiscali/indirizzi/recapiti/note, modifica, archivio e ripristino. Prospect e Cliente sono stati commerciali indipendenti da archivio/cestino. La prima vendita registrata converte automaticamente il Prospect nella stessa transazione. Un tentativo fallito o il solo censimento del pregresso non convertono. L'annullamento non retrocede automaticamente lo stato; questa scelta tecnica è documentata, non presentata come risposta originaria dell'utente.

Il contatto è un'identità condivisa tra uno o più clienti/prospect; sede facoltativa e dello stesso cliente per ciascuna relazione. Ruolo e indicatore principale sono specifici della relazione. È possibile associare un contatto esistente senza duplicarlo, modificare i recapiti condivisi e rimuovere una relazione mantenendone almeno una. Eliminare l'intera identità è un'azione distinta e dichiarata.

## Sicurezza e interfaccia

Sessione, BCrypt, CSRF, rate limiting, obbligo cambio password, logout e revoca per disabilitazione/reset. Nessuna 2FA in questa fase. Permessi server e controlli UI coerenti; ruolo ADMIN predefinito per gestione dati demo/reset. Il riepilogo non definisce una nuova durata/persistenza delle sessioni: configurazioni aggiuntive non vengono introdotte per supposizione.

Menu responsive, gerarchia chiara delle azioni, filtri espliciti, select leggibili e focus tastiera. Paginazione con solo conteggio su una pagina; su più pagine, conteggio separato e comandi affiancati con indicatore pagina. Il layout della scheda cliente mantiene il riferimento visivo fornito e usa soltanto dati reali o demo identificati.

## Demo e reset

Caricamento demo aggiuntivo, transazionale, una sola volta, con clienti/prospect e casi operativi variati. L'amministratore può azzerare tutti i dati CRM scrivendo RESET; conserva utenti/ruoli/permessi/audit, senza riutilizzare ID collegati alla cronologia. Il reset non è eseguito al deploy. Le tabelle aziendali sono elencate esplicitamente: nessun CASCADE verso tabelle di sicurezza.

## Limiti di ambito

I processi futuri di preventivi, ordini, contratti, rinnovi, assistenza e gestione erogazioni non hanno specifiche M2 dettagliate concordate nel riepilogo. Le estensioni esistenti sono mantenute, con assunzioni esplicite. Il riallineamento non ne dichiara la completezza funzionale.
