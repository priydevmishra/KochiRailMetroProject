package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);
    Optional<Department> findByName(String name);

    @Query("SELECT d FROM Department d WHERE d.notificationEnabled = true")
    List<Department> findNotificationEnabledDepartments();

    Boolean existsByCode(String code);

    List<Department> findByParentIsNull();
    List<Department> findByParentId(Long parentId);

    @Query("SELECT d FROM Department d WHERE d.parent IS NULL ORDER BY d.name")
    List<Department> findRootDepartments();

    @Query("SELECT d FROM Department d WHERE LOWER(d.name) = LOWER(:name)")
    Optional<Department> findByNameIgnoreCase(@Param("name") String name);

    List<Department> findByNameContainingIgnoreCase(String name);

    @Query("SELECT d FROM Department d WHERE d.code = :code")
    Optional<Department> findByCodeCustom(@Param("code") String code);
}
