# Immagini e accesso per 30 giorni

Clienti e prospect possono avere un logo, i contatti una foto e le installazioni hardware/software un'immagine propria. Il catalogo conserva il caricamento esistente. Senza immagine specifica, un'installazione usa quella del prodotto di catalogo collegato; rimuovere l'immagine personalizzata ripristina questa eredità. Senza immagini disponibili restano iniziali o icona.

Le immagini compaiono nelle intestazioni e nelle liste. Il logo cliente si gestisce con **Logo cliente** nella scheda; le foto sono disponibili nella rubrica e nei contatti del cliente; il dettaglio dell'installazione contiene **Immagine prodotto**. I file si caricano dopo aver salvato la scheda.

Formati PNG, JPEG e WEBP, limite 5 MiB. Il backend riconosce il formato dai dati, senza fidarsi del nome o del MIME dichiarato. Le immagini non sono pubbliche: lettura e modifica usano i permessi dei rispettivi moduli, con CSRF e audit per le modifiche. Non viene concesso accesso ad altre informazioni di catalogo quando un lettore del parco installato vede l'immagine ereditata.

V120 aggiunge soltanto colonne ai proprietari: dati precedenti conservati, immagini incluse nei backup PostgreSQL e nello stesso ciclo cestino/ripristino/cancellazione delle schede.

V121 introduce le tabelle Spring Session JDBC. Il cookie `PENNY_SESSION` è persistente, HttpOnly, SameSite=Lax e Secure quando l'accesso avviene in HTTPS. Sessione e cookie durano 30 giorni; il server applica inoltre una scadenza assoluta a 30 giorni dal login. Riavviare il backend non cancella le nuove sessioni. Logout, account disabilitato, reset password e incremento di session_version continuano a revocare l'accesso. Le sessioni in memoria precedenti richiedono un nuovo login una tantum al rilascio.

Il sottotitolo Penny diventa una frase con punteggiatura normale e resa in corsivo, simile a una citazione, senza i precedenti separatori grafici.
