package it.penny.api.products;
import it.penny.api.common.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={CatalogProductController.class,InstalledProductController.class,ProductTaxonomyController.class})
public class ProductErrorHandler {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<ApiError> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ApiError.of("PRODUCT_REQUEST_ERROR",e.getReason()==null?"Richiesta non valida":e.getReason()));}
 @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
 ResponseEntity<ApiError> concurrent(){return ResponseEntity.status(409).body(ApiError.of("CONCURRENT_UPDATE","Dati modificati da un’altra operazione. Ricarica e riprova."));}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ApiError> conflict(){return ResponseEntity.status(409).body(ApiError.of("PRODUCT_CONFLICT","Codice o matricola duplicati, oppure prodotto di catalogo già collegato: origine e categoria non modificabili"));}
}
