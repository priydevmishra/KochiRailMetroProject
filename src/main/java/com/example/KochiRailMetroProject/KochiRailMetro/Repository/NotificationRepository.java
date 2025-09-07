package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Notification;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserAndIsReadOrderByCreatedAtDesc(User user, Boolean isRead, Pageable pageable);

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isRead = false")
    Long countUnreadByUser(@Param("user") User user);

    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.priority = 'CRITICAL' AND n.isRead = false")
    Page<Notification> findCriticalUnreadByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.actionRequired = true AND n.user = :user AND n.isRead = false")
    Page<Notification> findActionRequiredByUser(@Param("user") User user, Pageable pageable);
}

