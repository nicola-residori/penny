package it.penny.api.workspace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceFileValidationTest {
 @Test void stripsPathsAndControlCharacters(){assertEquals("report.pdf",WorkspaceController.filename("C:\\temp\\report.pdf"));assertEquals("a_b_.pdf",WorkspaceController.filename("a\rb\n.pdf"));}
 @Test void sniffsInsteadOfTrustingClaimedContentType(){assertEquals("application/pdf",WorkspaceController.safeMime("%PDF-1.7".getBytes()));assertEquals("application/octet-stream",WorkspaceController.safeMime("<svg/onload=alert(1)>".getBytes()));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->WorkspaceController.safeMime(new byte[0]));}
}
