package com.wardk.meeteam_backend.domain.notification.repository;

import com.wardk.meeteam_backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
                select n from Notification n
                left join fetch n.receiver m
                left join fetch n.project p
                where m.id = :memberId
                order by n.createdAt desc
            """)
    Slice<Notification> findByMemberId(Long memberId, Pageable pageable);


    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.receiver.id = :memberId")
    void bulkIsRead(@Param("memberId") Long memberId);

    // 추후에 복합 인덱스 고려 요소 !!
    @Query("select count(*) from Notification n where n.receiver.id = :memberId and n.isRead = false")
    int findUnreadCount(@Param("memberId") Long memberId);

    /**
     * 특정 회원의 모든 알림 삭제
     */
    void deleteByReceiverId(Long receiverId);

    /**
     * 특정 프로젝트의 모든 알림 삭제
     */
    void deleteByProjectId(Long projectId);
}
