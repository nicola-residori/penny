# HTTPS sul reverse proxy esistente

- CT 110: Caddy su `192.168.1.6`.
- Nextcloud resta su `https://grezzano.homepc.it:8443`, upstream `192.168.1.5:80`.
- Penny usa `https://grezzano.homepc.it:9091`, upstream `192.168.0.20:80`.
- Caddy riutilizza il certificato pubblico del dominio già gestito e ne conserva il rinnovo automatico. Deve restare raggiungibile dall'esterno per la validazione ACME sulla porta 80 o 443, secondo la configurazione esistente.
- FritzBox: TCP esterna 9091 verso `192.168.1.6:9091`, sostituendo la precedente destinazione HTTP `192.168.0.20:80`. La regola 8443 esistente resta invariata.

La regola HSTS già inviata da Nextcloud si applica all'intero hostname, comprese le altre porte. Una seconda applicazione sullo stesso hostname deve quindi offrire HTTPS. Non è necessario cancellare HSTS o disabilitare le protezioni di Safari.

Il frontend Nginx conserva `X-Forwarded-Proto: https` soltanto quando arriva dal reverse proxy dedicato. Il backend è già configurato con `SERVER_FORWARD_HEADERS_STRATEGY=framework`; il token CSRF viene emesso con cookie Secure attraverso HTTPS. L'accesso HTTP locale conserva il proprio schema.

Verificare il certificato senza disabilitarne il controllo, il frontend, `/api/auth/csrf`, gli attributi dei cookie senza divulgarne i valori e il servizio Nextcloud esistente.
