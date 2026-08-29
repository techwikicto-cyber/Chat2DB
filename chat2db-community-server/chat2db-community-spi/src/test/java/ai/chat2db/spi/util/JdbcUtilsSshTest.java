package ai.chat2db.spi.util;

import ai.chat2db.community.domain.api.model.datasource.DataSourceConnect;
import ai.chat2db.community.domain.api.model.datasource.SSHInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testing a connection that has no SSH tunnel, which is nearly all of them.
 *
 * <p>{@code testConnect} read {@code ssh.isUse()} without checking whether
 * there was an ssh at all. Nothing had ever handed it a null, because the form
 * on the way in always sent an object - so when the SSH panel was removed from
 * that form and the stored value went through instead, every connection that
 * had never used SSH stopped opening, with a null pointer where a connection
 * error should have been.
 *
 * <p>What is asserted is narrow on purpose: that a missing SSH block is not
 * what fails. Everything past that point is driver loading and a real socket,
 * neither of which a unit test has here - so this proves the guard and claims
 * nothing else. The other end of the same bug, the client sending null in the
 * first place, is fixed in ConnectionEdit.
 */
class JdbcUtilsSshTest {

    private static final String UNREACHABLE = "jdbc:sqlserver://127.0.0.1:1;databaseName=nothing";

    @Test
    void noSshConfigurationIsNotWhatFails() {
        DataSourceConnect result = JdbcUtils.testConnect(
                UNREACHABLE, "127.0.0.1", "1", "sa", "irrelevant", "SQLSERVER", null, null, null);

        assertNotNull(result);
        assertMentionsNoSshFailure(result);
    }

    @Test
    void anSshBlockThatIsSwitchedOffIsNotWhatFailsEither() {
        SSHInfo unused = new SSHInfo();
        unused.setUse(false);

        DataSourceConnect result = JdbcUtils.testConnect(
                UNREACHABLE, "127.0.0.1", "1", "sa", "irrelevant", "SQLSERVER", null, unused, null);

        assertNotNull(result);
        assertMentionsNoSshFailure(result);
    }

    /** The connection failed, as it must - but not on the way past the ssh block. */
    private static void assertMentionsNoSshFailure(DataSourceConnect result) {
        String reported = result.getMessage() + String.valueOf(result.getErrorDetail());
        assertFalse(reported.contains("SSHInfo"), reported);
        assertFalse(reported.contains("isUse"), reported);
    }
}
