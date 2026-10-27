# Commit, push e installazione Proxmox

Autorizzazione: commit/push di tutto il progetto, nuovo CT su 192.168.1.4 e trasferimento dati attuali. Nessun merge su main richiesto. Nessuna modifica ai CT già presenti.

- [x] Escludere backup locali, credenziali, dump e artefatti. Verificare suite backend/frontend e compilazione prima del commit.
- [x] Preparare immagini di produzione (JAR Java 21 e frontend statico Nginx), Compose separato, controllo salute e documentazione di gestione.
- [x] Creare CT Debian non privilegiato, nesting/keyctl per Docker, 2 CPU, 3 GiB RAM, disco 24 GiB, DHCP su vmbr0 e avvio automatico. Scegliere ID libero dopo inventario.
- [x] Installare Docker dal repository ufficiale nel solo CT e avviare servizi con volumi persistenti. Pubblicare solo HTTP frontend.
- [x] Salvare dump consistente e snapshot verificabile del database locale, trasferire con SSH e ripristinare sul nuovo database vuoto. Conservare Mac e backup; nessun reset.
- [x] Confrontare dati storici e verificare HTTP/API/protezione autenticazione. Documentare indirizzo, CT e procedure di aggiornamento/backup.

Verifica pre-pubblicazione: 129 test backend e 108 frontend superati, build di produzione riuscita, ripristino atomico e confronto esatto di 1.331 record/23 tabelle alla versione Flyway 119. Il rilascio viene pubblicato sul branch `codex/proxmox-deployment` senza merge su main; il controllo finale confronta la revisione remota e l’avvio dopo reboot del CT.
