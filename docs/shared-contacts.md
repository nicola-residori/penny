# Contatti condivisi e prospect

V109 conserva gli ID dei contatti, i campi esistenti, le sedi e gli elementi nel cestino. Il nuovo `penny_contact_customers` contiene le associazioni canoniche: cliente/prospect obbligatorio, sede facoltativa appartenente allo stesso cliente, ruolo e indicazione di referente principale per associazione. I contatti preesistenti ricevono automaticamente l'associazione originaria, compresi quelli nel cestino. Gli inserimenti legacy vengono ancora associati automaticamente.

Il campo storico `penny_customer_contacts.customer_id` resta un riferimento di compatibilità e deve appartenere alle associazioni canoniche. L'API lo sposta quando viene rimossa l'associazione originaria. Un vincolo differito impedisce che un contatto rimanga senza associazioni anche tramite SQL diretto; le scritture bloccano la riga del contatto per serializzare le rimozioni concorrenti. Le nuove associazioni richiedono contatto e cliente attivi e una sede dello stesso cliente.

Il cestino del cliente conserva tutte le associazioni. Un contatto condiviso rimane disponibile attraverso gli altri clienti attivi. Le associazioni con clienti nel cestino restano nascoste e vengono conservate dal salvataggio dell'editor. Ripristinare il cliente le rende nuovamente visibili. Il cestino globale del contatto riguarda invece l'intera identità e tutte le associazioni.

La rimozione definitiva di un cliente con contatti condivisi viene **bloccata**, sia dal servizio del cestino sia dal database: ripristinare il cliente, rimuovere le sue associazioni condivise e riprovare. Questo evita che il vecchio `ON DELETE CASCADE` cancelli contatti ancora utilizzati. I contatti esclusivi continuano a essere rimossi insieme al cliente. Il messaggio del cestino indica come sbloccare l'operazione.

## API

Tutte le operazioni riutilizzano `CUSTOMERS_READ` / `CUSTOMERS_WRITE`.

- `GET /api/contacts?q=&page=0&size=20`: `{items,total,page,size}`, ricerca su nome, email, telefono e nome dei clienti collegati.
- `GET /api/contacts/{id}`: `id,fullName,email,phone,customerCount,links`.
- `POST /api/contacts`: `{fullName,email,phone,links:[{customerId,siteId,role,isPrimary}]}`; almeno un'associazione.
- `PUT /api/contacts/{id}`: stesso payload, sostituisce le associazioni visibili e conserva quelle con clienti nel cestino.
- `PUT /api/contacts/{id}/customers/{customerId}`: `{siteId,role,isPrimary}`, aggiunge o aggiorna l'associazione.
- `DELETE /api/contacts/{id}/customers/{customerId}`: rimuove solo l'associazione; respinge la rimozione dell'ultima associazione o dell'ultimo cliente attivo.
- Le rotte esistenti `GET/POST /api/customers/{id}/contacts` supportano ora i contatti condivisi e la sede facoltativa. La vecchia `DELETE /api/customers/{id}/contacts/{contactId}` rimuove solo l'associazione e applica gli stessi limiti.

Ogni link restituito include `customerId,customerName,lifecycleStatus,siteId,siteName,role,isPrimary`. `customerCount` conta le associazioni a clienti attivi.

Per le anagrafiche esistenti lo stato viene ricostruito dalle vendite: almeno una vendita registrata significa `CUSTOMER`, altrimenti `PROSPECT`. Le nuove anagrafiche iniziano come `PROSPECT` e passano automaticamente a `CUSTOMER` con la prima vendita registrata. Lo stato commerciale viene restituito dalle API e può essere usato come filtro nella lista clienti; non viene modificato manualmente dall'editor.
