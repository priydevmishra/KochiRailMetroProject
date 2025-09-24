package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ---------------- Basic finders ----------------
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    // ---------------- Department-based queries ----------------
    // Active users in a department
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.department = :department")
    List<User> findByDepartment(@Param("department") Department department);

    // Users by department code (all users, active or not)
    @Query("SELECT u FROM User u WHERE u.department.code = :departmentCode")
    List<User> findByDepartmentCode(@Param("departmentCode") String departmentCode);

    // Count employees in a department
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE u.department = :department AND r.name = 'EMPLOYEE'")
    long countEmployeesInDepartment(@Param("department") Department department);

    // ---------------- Fetch with department ----------------
    // Fetch user with department by username
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.username = :username")
    Optional<User> findByUsernameWithDepartment(@Param("username") String username);

    // Fetch user with department by id
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.id = :id")
    Optional<User> findByIdWithDepartment(@Param("id") Long id);

    // ---------------- Manager-specific queries ----------------
    // Find first manager in a department by department code
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'MANAGER' AND u.department.code = :code")
    Optional<User> findFirstManagerByDepartmentCode(@Param("code") String code);

    // All managers
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'MANAGER'")
    List<User> findAllManagers();

    // All employees
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'EMPLOYEE'")
    List<User> findAllEmployees();

    // Employees by department
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'EMPLOYEE' AND u.department = :department")
    List<User> findEmployeesByDepartment(@Param("department") Department department);

    // ---------------- Optional helper queries ----------------
    // Find user by username and department (optional)
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.department = :department")
    Optional<User> findByUsernameAndDepartment(@Param("username") String username, @Param("department") Department department);

}
