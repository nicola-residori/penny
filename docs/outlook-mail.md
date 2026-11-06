# Salva in Penny

## Installazione e uso

Istruzioni ospitate: `/outlook/install.html`. Manifest: `/outlook/manifest.xml`.

Windows: componente web per Outlook con caselle compatibili (Mailbox 1.8 minimo); selezione multipla su Mailbox 1.15. Il comando si trova nella superficie di lettura, quindi si usa su messaggi ricevuti/inviati, non bozze. Il manifest richiede ReadWriteMailbox per caricare selezioni multiple; il codice non usa API di invio/modifica/cancellazione. Non necessita di app Entra o token Exchange legacy.

Il dialogo “Collega Penny” autentica l'utente nel CRM e crea una chiave con ambito ristretto, 30 giorni, memorizzata solo come hash dal server. Il task pane conserva il token solo in memoria: alla riapertura va ricollegato (la sessione Penny può essere riutilizzata). Credenziali mai salvate nel componente. Revoca nella scheda cliente → Email → Collega Outlook. Cambio password/versione sessione, disabilitazione e rimozione permessi invalidano le connessioni.

Per Outlook classico Windows, `Save-PennyMail.ps1` usa COM e conserva il file MSG originale, oltre agli allegati separati. Permette di scegliere il messaggio o la conversazione che Outlook rende disponibile. Deve essere lanciato sul PC Windows con Outlook classico aperto; richiede Windows PowerShell 5.1 STA e criteri aziendali che permettano lo script. Non modificare execution policy per aggirare i criteri. Non funziona nel nuovo Outlook. Il token personale si crea nella stessa schermata del CRM e si incolla nella finestra del comando.

## Semantica

Matching esatto e normalizzato su email cliente, PEC e contatti condivisi; il proprio indirizzo viene escluso dai suggerimenti dal componente. Dominio e contenuto non determinano associazioni. Più corrispondenze richiedono scelta. Tutti i salvataggi richiedono conferma del cliente. Nessun LLM, sincronizzazione o lettura in background.

Email e attività hanno relazione molti-a-molti, limitata allo stesso cliente anche tramite FK composite. Spostare un'attività a un altro cliente richiede prima di scollegarne le email. Permessi di documenti + clienti per leggere/salvare; il relativo permesso attività è necessario per visualizzare/gestire i collegamenti. I commenti, con autore/data, sono interni e append-only. Il reset amministrativo comprende email, allegati, commenti e collegamenti; conserva identità e configurazione delle connessioni, come le altre configurazioni di sicurezza.

Deduplica atomica per cliente + internetMessageId; in sua assenza casella + ID Outlook. Nessuna deduplica per solo oggetto. Il salvataggio di un messaggio è transazionale, inclusi tutti gli allegati. Un batch può terminare dopo alcuni messaggi: la UI ne indica il numero, un retry non duplica quelli già salvati. Originali/allegati sono privati nel database, scaricati come attachment/no-store/octet-stream. Il testo nel CRM viene escapato, mai eseguito come HTML; nessuna immagine remota.

20 MB massimi per messaggio sommando originale e allegati separati; massimo 100 allegati, 10 MB ciascuno. Il formato EML originale richiede Mailbox 1.14: sui client precedenti la copia MIME ricostruita viene esplicitamente indicata. Allegati cloud/protetti non recuperabili fanno fallire il messaggio, senza omissioni silenziose. La selezione multipla è una selezione dell'utente, non una scansione automatica di tutta la conversazione.

## Verifica

Test backend di payload, deduplica, matching condiviso, integrità dei bytes, rollback, revoca, permessi e isolamento bearer/sessione. Test UI ricerca/commenti/associazione inversa e testo ostile. Contract test Office.js (`npm run test:outlook`), validazione manifest Microsoft, parsing script PowerShell, controlli browser desktop/mobile/chiaro/scuro con API simulate. Il collaudo dentro Outlook Windows e COM richiede un PC e una casella reali e non è sostituibile dai mock.
