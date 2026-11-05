package com.wardk.meeteam_backend.adminloginunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic input only. No deployed password, server, Docker or credential store. */
class AdminPasswordHashSetupTest {
 @TempDir Path directory;
 private final String synthetic="synthetic-tool-input-only-12345";
 @Test void toolWritesOnlyCost12HashWithOwnerOnlyPermissions() throws Exception {
  Path output=directory.resolve("admin.hash");
  AdminPasswordSetupTool.storeHash(output,synthetic.toCharArray(),synthetic.toCharArray());
  String content=Files.readString(output); String hash=content.strip();
  assertThat(content).doesNotContain(synthetic).endsWith("\n");
  assertThat(hash).startsWith("$2a$12$");
  assertThat(new BCryptPasswordEncoder().matches(synthetic,hash)).isTrue();
  assertThat(Files.getPosixFilePermissions(output)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
 }
 @Test void invalidOrMismatchingInputNeverCreatesCredentialFile() {
  List<String> bad=Arrays.asList(null,"short"," ".repeat(25),"한".repeat(25));
  for(String value:bad) {
   Path output=directory.resolve("invalid.hash");
   assertThatThrownBy(()->AdminPasswordSetupTool.storeHash(output,value==null?null:value.toCharArray(),value==null?null:value.toCharArray()))
       .isInstanceOf(IllegalArgumentException.class);
   assertThat(Files.exists(output)).isFalse();
  }
  Path output=directory.resolve("mismatch.hash");
  assertThatThrownBy(()->AdminPasswordSetupTool.storeHash(output,synthetic.toCharArray(),"other-synthetic-input-long".toCharArray()))
      .isInstanceOf(IllegalArgumentException.class);
  assertThat(Files.exists(output)).isFalse();
 }
 @Test void existingFileAndSymlinkAreNeverOverwrittenOrDeleted() throws Exception {
  Path existing=directory.resolve("existing"); Files.writeString(existing,"existing-owner-data");
  assertThatThrownBy(()->AdminPasswordSetupTool.storeHash(existing,synthetic.toCharArray(),synthetic.toCharArray()))
      .isInstanceOf(FileAlreadyExistsException.class);
  assertThat(Files.readString(existing)).isEqualTo("existing-owner-data");
  Path link=directory.resolve("link"); Files.createSymbolicLink(link,existing);
  assertThatThrownBy(()->AdminPasswordSetupTool.storeHash(link,synthetic.toCharArray(),synthetic.toCharArray()))
      .isInstanceOf(FileAlreadyExistsException.class);
  assertThat(Files.isSymbolicLink(link)).isTrue();
  assertThat(Files.readString(existing)).isEqualTo("existing-owner-data");
 }
 @Test void hashOnlyBootstrapUsesSameStoredHashAndAuthenticatesActualPassword() {
  var encoder=new BCryptPasswordEncoder(10); String hash=encoder.encode(synthetic);
  var members=mock(MemberRepository.class); var jwt=mock(JwtUtil.class);
  var service=new AdminAccountService(members,encoder,jwt,"99990002","",hash);
  when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.empty());
  service.run(null);
  verify(members).save(argThat(member->member.getPassword().equals(hash)&&member.isLocalAdminAccount()));
  var stored=Member.createForLocalAdmin(AdminAccountService.EMAIL,hash);
  when(members.findByEmailAndDeletedAtIsNull(AdminAccountService.EMAIL)).thenReturn(Optional.of(stored));
  service.login("99990002",synthetic);
  verify(jwt).createAccessToken(stored); verify(jwt).createRefreshToken(stored);
 }
 @Test void hashOnlyRestartPreservesIdentityAndRejectsDifferentConfigurationHash() {
  var encoder=new BCryptPasswordEncoder(10); String hash=encoder.encode(synthetic);
  var members=mock(MemberRepository.class); var jwt=mock(JwtUtil.class);
  var stored=Member.createForLocalAdmin(AdminAccountService.EMAIL,hash);
  when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.of(stored));
  new AdminAccountService(members,encoder,jwt,"99990002","",hash).run(null);
  String different=encoder.encode("different-synthetic-input-only");
  assertThatThrownBy(()->new AdminAccountService(members,encoder,jwt,"99990002","",different).run(null))
      .isInstanceOf(IllegalStateException.class);
  assertThat(stored.getPassword()).isEqualTo(hash);
  verify(members,never()).save(any());
 }
 @Test void ambiguousMalformedOrExcessiveCostConfigurationIsRejectedWithoutValuesInError() {
  var encoder=new BCryptPasswordEncoder(10); String hash=encoder.encode(synthetic);
  var members=mock(MemberRepository.class); var jwt=mock(JwtUtil.class);
  for(String invalid:List.of("invalid-hash","$2a$31$"+"A".repeat(53),"$2a$04$"+"A".repeat(53))) {
   assertThatThrownBy(()->new AdminAccountService(members,encoder,jwt,"99990002","",invalid))
       .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(invalid);
  }
  assertThatThrownBy(()->new AdminAccountService(members,encoder,jwt,"99990002",synthetic,hash))
      .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(synthetic).hasMessageNotContaining(hash);
  verifyNoInteractions(members,jwt);
 }
 @Test void nonInteractiveCliRefusesToCollectOrWriteAnyPassword() throws Exception {
  String javaBin=Path.of(System.getProperty("java.home"),"bin","java").toString();
  String classes=Path.of(AdminPasswordSetupTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  Path output=directory.resolve("no-console.hash");
  String crypto=Path.of(BCryptPasswordEncoder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  String logging=Path.of(org.apache.commons.logging.LogFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  String classpath=String.join(java.io.File.pathSeparator,classes,crypto,logging);
  var child=new ProcessBuilder(javaBin,"-cp",classpath,AdminPasswordSetupTool.class.getName(),output.toString()).redirectErrorStream(true).start();
  assertThat(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
  assertThat(child.exitValue()).isEqualTo(2);
  assertThat(new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).contains("터미널");
  assertThat(Files.exists(output)).isFalse();
 }
}
