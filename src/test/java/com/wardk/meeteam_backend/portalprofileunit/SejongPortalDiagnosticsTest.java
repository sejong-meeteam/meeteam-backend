package com.wardk.meeteam_backend.global.auth.client;

import com.wardk.meeteam_backend.global.exception.CustomException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SejongPortalDiagnosticsTest {
    @Test
    void manualDiagnosticsExposeOnlyFixedStageCodesAndStatusNotIdentityOrCredentials() throws Exception {
        var transport=mock(CloseableHttpClient.class);
        var calls=new AtomicInteger();
        var diagnostic=new ArrayList<String>();
        when(transport.execute(any(ClassicHttpRequest.class),any(HttpContext.class),SejongPortalDiagnosticsTest.<String>anyHandler()))
                .thenAnswer(invocation->{
                    HttpClientResponseHandler<String> handler=invocation.getArgument(2);
                    int stage=calls.getAndIncrement();
                    var response=new BasicClassicHttpResponse(stage < 3 ? 200 : 302);
                    response.setEntity(new StringEntity(stage==0 ? "<html>Login form</html>" : stage==1 ? "var result = 'OK'" : stage==2 ? "<html>SSO handoff</html>" : "synthetic-private-body",ContentType.TEXT_HTML));
                    return handler.handleResponse(response);
                });
        try(var client=new SejongPortalClient(transport,diagnostic::add)) {
            assertThrows(CustomException.class,()->client.authenticateAndFetchProfile("20261234","synthetic-private-password"));
        }
        assertEquals(List.of("LOGIN_PAGE_REQUEST","LOGIN_PAGE_HTTP_200","LOGIN_REQUEST","LOGIN_HTTP_200","LOGIN_RESULT_OK","LOGIN_ACCEPTED","SSO_REQUEST","SSO_HTTP_200","PROFILE_REQUEST","PROFILE_HTTP_302","FAIL_IOException"),diagnostic);
        assertEquals(4,calls.get());
        assertFalse(String.join(" ",diagnostic).contains("20261234"));
        assertFalse(String.join(" ",diagnostic).contains("synthetic-private"));
    }
    private static <T> HttpClientResponseHandler<T> anyHandler() { return any(); }
}
