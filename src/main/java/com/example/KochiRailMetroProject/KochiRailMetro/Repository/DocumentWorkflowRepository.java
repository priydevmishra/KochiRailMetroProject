package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentWorkflowRepository extends JpaRepository<DocumentWorkflow, Long> {

    Page<DocumentWorkflow> findByAssignedTo(User assignedTo, Pageable pageable);

    @Query("SELECT dw FROM DocumentWorkflow dw WHERE dw.assignedTo = :user AND dw.currentStatus IN :statuses")
    Page<DocumentWorkflow> findByAssignedToAndStatusIn(@Param("user") User user,
                                                       @Param("statuses") List<DocumentWorkflow.WorkflowStatus> statuses,
                                                       Pageable pageable);

    @Query("SELECT dw FROM DocumentWorkflow dw WHERE dw.deadline <= :deadline AND dw.currentStatus = 'PENDING'")
    List<DocumentWorkflow> findUpcomingDeadlines(@Param("deadline") LocalDateTime deadline);

    @Query("SELECT dw FROM DocumentWorkflow dw WHERE dw.deadline < :now AND dw.currentStatus = 'PENDING'")
    List<DocumentWorkflow> findOverdueWorkflows(@Param("now") LocalDateTime now);

    List<DocumentWorkflow> findByWorkflowTypeAndCurrentStatus(DocumentWorkflow.WorkflowType type,
                                                              DocumentWorkflow.WorkflowStatus status);
}
