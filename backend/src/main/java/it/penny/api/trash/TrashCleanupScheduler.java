package it.penny.api.trash;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="penny.trash.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class TrashCleanupScheduler {
 private final TrashService trash;private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(TrashCleanupScheduler.class);
 public TrashCleanupScheduler(TrashService trash){this.trash=trash;}
 @Scheduled(fixedDelayString="${penny.trash.cleanup-interval-ms:3600000}",initialDelayString="${penny.trash.cleanup-initial-delay-ms:60000}")
 public void cleanup(){try{var result=trash.purgeExpired();log.info("Trash retention cleanup: {} purged, {} blocked",result.get("purged"),result.get("blocked"));}catch(RuntimeException e){log.error("Trash cleanup failed; retained records will be retried",e);}}
}
