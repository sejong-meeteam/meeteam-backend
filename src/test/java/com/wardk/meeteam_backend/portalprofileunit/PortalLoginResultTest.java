package com.wardk.meeteam_backend.global.auth.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalLoginResultTest {
    @Test
    void successIgnoresIncidentalErrorHandlerReferences() {
        assertEquals(PortalLoginResult.Status.OK, PortalLoginResult.parse("<script>var result = 'OK'; if (result == 'erridpwd') alert('invalid'); else if (result === 'pwdNeedChg') reset(); else if (result == 'noaccess') deny();</script>"));
    }

    @Test
    void acceptsEitherQuoteAndAssignmentWhitespace() {
        for (String html : new String[]{"var result='OK';", "let result \t=\n \"OK\";", "result = 'OK'", " OK "}) {
            assertEquals(PortalLoginResult.Status.OK, PortalLoginResult.parse(html));
        }
    }

    @Test
    void recognizesActualFailureAssignments() {
        assertEquals(PortalLoginResult.Status.INVALID_CREDENTIALS, PortalLoginResult.parse("var result = 'erridpwd';"));
        assertEquals(PortalLoginResult.Status.RESET_REQUIRED, PortalLoginResult.parse("result=\"pwdNeedChg\";"));
        assertEquals(PortalLoginResult.Status.ACCESS_DENIED, PortalLoginResult.parse("result = 'noaccess';"));
    }

    @Test
    void rejectsDuplicateAssignmentsEvenWhenValuesAgree() {
        assertEquals(PortalLoginResult.Status.AMBIGUOUS, PortalLoginResult.parse("var result='OK'; result='erridpwd';"));
        assertEquals(PortalLoginResult.Status.AMBIGUOUS, PortalLoginResult.parse("var result='OK'; result='OK';"));
    }

    @Test
    void missingUnknownOrComparisonOnlyResultsAreNotSuccess() {
        for (String html : new String[]{"", "<html>Login form</html>", "result == 'OK'", "result === 'OK'", "var result='unexpected';"}) {
            assertEquals(PortalLoginResult.Status.UNKNOWN, PortalLoginResult.parse(html));
        }
        assertEquals(PortalLoginResult.Status.UNKNOWN, PortalLoginResult.parse(null));
    }
}
