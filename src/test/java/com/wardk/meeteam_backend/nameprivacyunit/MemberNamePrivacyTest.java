package com.wardk.meeteam_backend.nameprivacyunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.member.service.MemberProfileServiceImpl;
import com.wardk.meeteam_backend.web.member.dto.request.MemberProfileUpdateRequest;
import com.wardk.meeteam_backend.web.member.dto.response.*;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberNamePrivacyTest {
    private Member member(String name) { var m=Member.createForTest("synthetic@example.invalid",name);ReflectionTestUtils.setField(m,"id",7L);return m; }
    @Test void defaultsToPublicWithoutChangingStoredName() {var m=member("김민수");assertThat(m.isNamePrivate()).isFalse();assertThat(m.getPublicName()).isEqualTo("김민수");}
    @Test void masksOnlyAtResponseTime() {var m=member("김민수");m.updateNamePrivacy(true);assertThat(m.getPublicName()).isEqualTo("김**");assertThat(m.getRealName()).isEqualTo("김민수");m.updateNamePrivacy(false);assertThat(m.getPublicName()).isEqualTo("김민수");}
    @Test void omissionPreservesPreviousPreference() {var m=member("김민수");m.updateNamePrivacy(true);m.updateNamePrivacy(null);assertThat(m.isNamePrivate()).isTrue();}
    @Test void aSingleCodePointIsFullyMasked() {var m=member("김");m.updateNamePrivacy(true);assertThat(m.getPublicName()).isEqualTo("*");}
    @Test void supplementaryUnicodeIsNotSplit() {var m=member("\uD842\uDFB7민수");m.updateNamePrivacy(true);assertThat(m.getPublicName()).isEqualTo("\uD842\uDFB7**");}
    @Test void ownResponsesKeepRealNameAndExposePreference() throws Exception {var m=member("김민수");m.updateNamePrivacy(true);var json=new ObjectMapper().findAndRegisterModules();for(var dto:new Object[]{new MemberProfileResponse(m,7L),new MemberProfileUpdateResponse(m)}){var node=json.valueToTree(dto);assertThat(node.path("name").asText()).isEqualTo("김민수");assertThat(node.path("namePrivate").asBoolean()).isTrue();}}
    @Test void profileUpdatePersistsPreferenceAndOldClientDoesNotResetIt() {var m=member("김민수");var repo=mock(MemberRepository.class);when(repo.findById(7L)).thenReturn(Optional.of(m));when(repo.save(any())).thenAnswer(i->i.getArgument(0));var service=new MemberProfileServiceImpl(repo,null,null,null,null,null);var req=new MemberProfileUpdateRequest();req.setName("김민수");req.setNamePrivate(true);assertThat(service.updateProfile(7L,req,null).isNamePrivate()).isTrue();req.setNamePrivate(null);assertThat(service.updateProfile(7L,req,null).isNamePrivate()).isTrue();req.setNamePrivate(false);assertThat(service.updateProfile(7L,req,null).isNamePrivate()).isFalse();}
    @Test void schoolVerifiedNameRemainsImmutableWhilePrivacyCanChange() {var m=member("김민수");ReflectionTestUtils.setField(m,"schoolProfileVerified",true);m.updateNamePrivacy(true);assertThatCode(()->m.validateProfileNameChange(null)).doesNotThrowAnyException();assertThatThrownBy(()->m.validateProfileNameChange("다른이름")).isInstanceOf(com.wardk.meeteam_backend.global.exception.CustomException.class);assertThat(m.getRealName()).isEqualTo("김민수");}
    @Test void notificationActorNamesUsePublicRepresentation() {var m=member("김민수");m.updateNamePrivacy(true);var event=mock(NotificationEvent.class);assertThat(NotificationContext.of(event,null,m,null).getActorName()).isEqualTo("김**");}
}
