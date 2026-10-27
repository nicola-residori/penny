# Contatti condivisi e Prospect/Cliente

Ogni contatto è una sola identità condivisa: nome, email e telefono si aggiornano per tutti i clienti/prospect associati. La relazione al cliente contiene ruolo, indicatore principale e sede facoltativa; la sede deve appartenere al cliente della relazione. È obbligatoria almeno una relazione. I contatti storici mantengono la relazione originale, senza deduplicazioni automatiche basate su nome/email.

La scheda Contatti consente di creare, associare un contatto già esistente, modificare identità/relazioni, rimuovere un'associazione lasciandone almeno una e spostare l'intero contatto nel cestino per tutti. Un cliente nel cestino non nasconde il contatto agli altri clienti attivi associati. Per sicurezza, la cancellazione definitiva di un cliente con contatti condivisi resta bloccata: ripristinare prima il cliente e rimuovere le associazioni condivise. I contatti condivisi non vengono cancellati in cascata.

Lo stato commerciale è PROSPECT o CUSTOMER, separato da archivio/cestino e dal tipo azienda/privato. In creazione è predefinito Prospect, senza scelta manuale; lo stato storico viene ricostruito dalle vendite registrate. La scheda mostra il badge e un filtro per stato. La prima vendita confermata converte automaticamente il prospect in cliente, senza pulsanti o modifica manuale. Un inserimento del pregresso senza vendita non converte lo stato. La transizione è tracciata nell'audit. I permessi sono CUSTOMERS_READ/WRITE; l'eliminazione globale richiede anche TRASH_DELETE.

La migrazione V109 aggiunge le relazioni e lo stato senza modificare anagrafiche, contatti, sedi, date di eliminazione o voci cestino preesistenti. Il campo customer_id storico resta come riferimento di compatibilità, mantenuto coerente con le relazioni canoniche.

L’annullamento di una vendita non retrocede automaticamente il cliente a prospect.
