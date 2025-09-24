package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.GmailSyncHistory;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GmailSyncHistoryRepository extends JpaRepository<GmailSyncHistory, Long> {

    // Check if a Gmail message has already been synced by any user
    boolean existsByGmailMessageId(String gmailMessageId);

    // Check if a Gmail message has already been synced by a specific user (NEW METHOD)
    boolean existsByGmailMessageIdAndUser(String gmailMessageId, User user);

    // Get sync history for a specific user and message
    Optional<GmailSyncHistory> findByGmailMessageIdAndUser(String gmailMessageId, User user);

    // Get all synced message IDs for a user
    @Query("SELECT g.gmailMessageId FROM GmailSyncHistory g WHERE g.user = :user")
    Set<String> findSyncedMessageIdsByUser(@Param("user") User user);

    // Get last sync time for a user
    @Query("SELECT MAX(g.syncedAt) FROM GmailSyncHistory g WHERE g.user = :user")
    Optional<LocalDateTime> findLastSyncTimeByUser(@Param("user") User user);

    // Count synced emails for a user
    long countByUser(User user);

    // Additional helper methods for better tracking
    @Query("SELECT COUNT(g) FROM GmailSyncHistory g WHERE g.user = :user AND g.syncedAt >= :fromDate")
    long countByUserAndSyncedAtAfter(@Param("user") User user, @Param("fromDate") LocalDateTime fromDate);

    // Get all sync history for a user (with pagination support if needed)
    @Query("SELECT g FROM GmailSyncHistory g WHERE g.user = :user ORDER BY g.syncedAt DESC")
    List<GmailSyncHistory> findByUserOrderBySyncedAtDesc(@Param("user") User user);
}