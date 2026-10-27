package it.penny.api.trash;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/trash")
public class TrashController {
 private final TrashService trash;
 public TrashController(TrashService trash){this.trash=trash;}
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){return trash.list(q,page,size,auth);}
 @DeleteMapping("/{entityType}/{entityId}") public Map<String,Object> move(@PathVariable String entityType,@PathVariable long entityId,Authentication auth){return trash.move(entityType,entityId,auth);}
 public record EmptyRequest(Boolean confirm) {}
 @PostMapping("/empty") public Map<String,Integer> empty(@RequestBody(required=false) EmptyRequest request,Authentication auth){return trash.empty(request!=null&&Boolean.TRUE.equals(request.confirm()),auth);}
 @PostMapping("/{entryId}/restore") public Map<String,Object> restore(@PathVariable long entryId,Authentication auth){return trash.restore(entryId,auth);}
}
