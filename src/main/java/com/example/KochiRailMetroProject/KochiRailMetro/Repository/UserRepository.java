package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.department = ?1")
    List<User> findByDepartment(Department department);

    @Query("SELECT u FROM User u WHERE u.department.code = ?1")
    List<User> findByDepartmentCode(String departmentCode);
}
