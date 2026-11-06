# Salva in Penny — piano approvato nel flusso della chat

Obiettivo: salvataggio esplicito, deterministico, da Outlook Windows nella scheda cliente. Nessuna lettura automatica della casella, nessun LLM, nessun invio di posta.

1. Backend: migrazione V132 additiva per connessioni revocabili (token hash, 30 giorni, versione credenziali), messaggi, allegati. API sessione per lettura e connessioni; bridge bearer limitato a ricerca cliente/salvataggio. Permessi CUSTOMERS_READ + DOCUMENTS_READ/WRITE già presenti. Matching esatto normalizzato su email cliente, PEC e contatti condivisi; ambiguità confermata dall'utente. Deduplica per cliente/message-id, transazione e limiti 20 MB complessivi. Test di matching, allegati, duplicati, rollback e autorizzazioni.
2. Scheda Email: componenti comuni Button/SearchControl/Table/DetailModal, tema esistente, ricerca/paginazione, conversazioni raggruppate per conversation-id, corpo testo (mai HTML attivo), download originali/allegati, gestione connessioni Outlook. Checklist: tastiera, stati caricamento/vuoto/errore, layout mobile, nessun input nativo duplicato.
3. Add-in Office.js: manifest HTTPS, comando lettura Salva in Penny, login in dialogo separato, token bridge in memoria, suggerimenti + ricerca cliente + conferma, estrazione allegati senza omissioni silenziose, EML originale quando API disponibile. API 1.8 minima, selezione multipla su 1.15. Le selezioni multiple NON vengono presentate come intero thread.
4. Outlook classico Windows: comando PowerShell COM per messaggio/conversazione disponibile (MSG originale e allegati), stessa API/token revocabile. Documentare installazione, prerequisiti e limiti; nessuna esecuzione automatica di script sul PC utente.
5. Verifica su CT111 in progetto Docker separato, dati di test isolati; build e test frontend/backend, contract test add-in con mock Office.js e rendering. Backup DB prima della migrazione e rilascio controllato su penny-crm, branch dedicato senza merge.

Review focus: contatto associato a due clienti; doppio clic e concorrenza; messaggio/HTML ostile; allegato non scaricabile; revoca o disabilitazione utente; reset CRM completo; nessun segreto in log/URL.

Estensione richiesta durante lo sviluppo: ricerca su oggetto/indirizzi/testo/commenti, commenti interni append-only, collegamenti molti-a-molti email/attività dello stesso cliente. Lettura/scrittura attività verificata anche dal backend, opzioni paginate; dettaglio attività mostra email e permette associazione inversa. La UI cliente usa gli stessi componenti comuni.

Riferimenti Microsoft verificati: https://learn.microsoft.com/en-us/office/dev/add-ins/outlook/item-multi-select (Mailbox 1.15, ReadWriteMailbox, unloadAsync); https://learn.microsoft.com/en-us/outlook/add-ins/get-attachments-of-an-outlook-item (Mailbox 1.8); esportazione EML getAsFileAsync: Mailbox 1.14. Nessun token Exchange legacy, Graph o LLM necessario per questa implementazione.
