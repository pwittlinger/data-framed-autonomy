package translations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import model.RecoveryStrategy;

class NandGuardEncoderTest {

  @TempDir
  Path temp;

  @Test
  void declareOnlyDomainAllowsGoalStatesToReplay() throws Exception {
    String domain = Files.readString(NandGuardEncoder.exportClausesDomain(temp, RecoveryStrategy.RESET, true));
    String validatePayload = validatePayload(domain);
    assertTrue(validatePayload.contains("DECLARE-only: goal states may replay events"));
    assertFalse(validatePayload.contains("\n            (not (goal_state ?s1))"));
  }

  @Test
  void mixedDomainKeepsGoalStateGuard() throws Exception {
    String domain = Files.readString(NandGuardEncoder.exportClausesDomain(temp, RecoveryStrategy.RESET, false));
    assertTrue(validatePayload(domain).contains("(not (goal_state ?s1))"));
  }

  private static String validatePayload(String domain) {
    int start = domain.indexOf("(:action validate-payload");
    int end = domain.indexOf(":effect", start);
    return domain.substring(start, end);
  }
}
