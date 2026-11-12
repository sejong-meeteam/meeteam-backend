package com.wardk.meeteam_backend.nameprivacyunit;
import com.querydsl.jpa.impl.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepositoryImpl;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepositoryImpl;
import com.wardk.meeteam_backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamePrivacySearchPolicyTest {
 @Test void memberNameSearchIsRestrictedToPublicNames() {
  var repo=new MemberRepositoryImpl(mock(JPAQueryFactory.class));
  Object expression=ReflectionTestUtils.invokeMethod(repo,"nameContains","민수");
  assertThat(expression.toString()).contains("namePrivate = false","realName");
  assertThat((Object)ReflectionTestUtils.invokeMethod(repo,"nameContains", " ")).isNull();
 }
 @Test void projectTitleSearchRemainsButCreatorNameSearchRequiresPublicName() {
  var repo=new ProjectRepositoryImpl(mock(JPAQueryFactory.class));
  Object expression=ReflectionTestUtils.invokeMethod(repo,"keywordContains","합성검색");
  assertThat(expression.toString()).contains("project.name","namePrivate = false","creator.realName");
 }
 @Test void privateNamesSortByConstantAndIdNotHiddenCharacters() {
  var repo=new MemberRepositoryImpl(mock(JPAQueryFactory.class));var query=new JPAQuery<>();
  ReflectionTestUtils.invokeMethod(repo,"applySafeMemberSorting",query,PageRequest.of(0,10,Sort.by("realName")));
  var orders=query.getMetadata().getOrderBy();
  assertThat(orders).hasSize(2);
  assertThat(orders.get(0).toString()).contains("namePrivate","*").doesNotContain("substring");
  assertThat(orders.get(1).getTarget()).isEqualTo(com.wardk.meeteam_backend.domain.member.entity.QMember.member.id);
 }
 @Test void nestedMemberRealNameSortingIsRejected() {
  var repo=new MemberRepositoryImpl(mock(JPAQueryFactory.class));
  assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(repo,"applySafeMemberSorting",new JPAQuery<>(),PageRequest.of(0,10,Sort.by("realName.length")))).isInstanceOf(CustomException.class);
 }
 @Test void projectCreatorNameSortingIsRejectedBeforeAnyQuery() {
  var repo=new ProjectRepositoryImpl(mock(JPAQueryFactory.class));
  assertThatThrownBy(()->repo.searchWithSlice(null,PageRequest.of(0,10,Sort.by("creator.realName")))).isInstanceOf(CustomException.class);
 }
}
