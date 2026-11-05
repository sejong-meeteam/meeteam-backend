package com.wardk.meeteam_backend.adminloginunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.cookie.*;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.auth.service.dto.TokenExchangeResult;
import com.wardk.meeteam_backend.global.config.CookieProperties;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.global.response.*;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.controller.AuthController;
import com.wardk.meeteam_backend.web.auth.dto.sejong.SejongLoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone HTTP contract; no live portal, database, cookies or credentials. */
@ExtendWith(MockitoExtension.class)
class AdminExistingLoginApiTest {
 @Mock MemberRepository members;
 @Mock SejongPortalClient portal;
 @Mock ObjectProvider<TestAccountService> testAccounts;
 @Mock AdminAccountService admins;
 @Mock JwtUtil jwt;
 @InjectMocks AuthService auth;
 private MockMvc mvc;
 @BeforeEach void setup() {
  ReflectionTestUtils.setField(auth,"adminAccountService",admins);
  var properties=mock(CookieProperties.class);
  // Actual cookie providers are exercised; stubs are used only on the successful request.
  lenient().when(properties.isSecure()).thenReturn(true);
  lenient().when(properties.getSameSite()).thenReturn("None");
  lenient().when(jwt.getAccessExpirationTime()).thenReturn(3600000L);
  lenient().when(jwt.getRefreshExpirationTime()).thenReturn(1209600000L);
  mvc=MockMvcBuilders.standaloneSetup(new AuthController(auth,
      new RefreshTokenCookieProvider(properties,jwt),new AccessTokenCookieProvider(properties,jwt)))
      .setControllerAdvice(new GlobalExceptionHandler()).build();
 }
 @Test void existingSejongEndpointReturnsUnchangedBodyAndTwoHttpOnlyCookiesForAdmin() throws Exception {
  when(admins.matchesUsername("99123456")).thenReturn(true);
  when(admins.login("99123456","synthetic-input")).thenReturn(new TokenExchangeResult("synthetic-access","synthetic-refresh"));
  var result=mvc.perform(post("/api/v1/auth/login/sejong").contentType(MediaType.APPLICATION_JSON)
      .content("{\"studentId\":\"99123456\",\"password\":\"synthetic-input\"}"))
      .andExpect(status().isOk())
      .andExpect(content().json(new ObjectMapper().writeValueAsString(SuccessResponse.onSuccess(SejongLoginResponse.existingMember()))))
      .andReturn();
  assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2)
      .allSatisfy(cookie -> assertThat(cookie).contains("HttpOnly","Secure","SameSite=None","Path=/"));
  assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-access","synthetic-refresh");
  verifyNoInteractions(portal,members,testAccounts);
 }
 @Test void badAdminPasswordReturns401WithoutCookieOrPortalFallback() throws Exception {
  when(admins.matchesUsername("99123456")).thenReturn(true);
  when(admins.login("99123456","synthetic-wrong")).thenThrow(new CustomException(ErrorCode.BAD_CREDENTIALS));
  mvc.perform(post("/api/v1/auth/login/sejong").contentType(MediaType.APPLICATION_JSON)
      .content("{\"studentId\":\"99123456\",\"password\":\"synthetic-wrong\"}"))
      .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  verifyNoInteractions(portal,members,testAccounts,jwt);
 }
}
