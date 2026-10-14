package com.wardk.meeteam_backend.fixture;

import com.wardk.meeteam_backend.domain.member.entity.Member;

/**
 * Member 테스트 픽스처.
 */
public class MemberFixture {

    public static Member defaultMember() {
        return Member.createForTest("test@example.com", "테스트유저");
    }

    public static Member withName(String name) {
        return Member.createForTest(name.toLowerCase() + "@example.com", name);
    }

    public static Member withEmail(String email, String name) {
        return Member.createForTest(email, name);
    }
}
