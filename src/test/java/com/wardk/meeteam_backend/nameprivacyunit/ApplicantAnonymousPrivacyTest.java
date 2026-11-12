package com.wardk.meeteam_backend.nameprivacyunit;
import com.wardk.meeteam_backend.web.application.controller.ApplicationController;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ApplicantAnonymousPrivacyTest {
 @Test void ownApplicationFormUsesOnlyTheAuthenticatedMemberIdentity() {
  var service=mock(ProjectApplicationService.class);var controller=new ApplicationController(service);
  var principal=mock(com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails.class);
  when(principal.getMemberId()).thenReturn(7L);
  controller.getApplicationPage(10L,principal);
  verify(service).getApplicationPage(10L,7L);
  verifyNoMoreInteractions(service);
 }
 @Test void anonymousRequestsCannotReachApplicantNameServices() {
  var service=mock(ProjectApplicationService.class);var controller=new ApplicationController(service);
  assertThatThrownBy(()->controller.getApplicationList(10L,null)).isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  assertThatThrownBy(()->controller.getApplicationDetail(10L,99L,null)).isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  verifyNoInteractions(service);
 }
}
