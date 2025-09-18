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

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    // 🔹 Fetch users by department (only active users)
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.department = ?1")
    List<User> findByDepartment(Department department);

    // 🔹 Fetch users by department code
    @Query("SELECT u FROM User u WHERE u.department.code = ?1")
    List<User> findByDepartmentCode(String departmentCode);

    // 🔹 Count employees in department
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r " +
            "WHERE u.department = :department AND r.name = 'EMPLOYEE'")
    long countEmployeesInDepartment(@Param("department") Department department);

    // 🔹 New queries to fetch user along with department (for authentication)
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.username = :username")
    Optional<User> findByUsernameWithDepartment(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.id = :id")
    Optional<User> findByIdWithDepartment(@Param("id") Long id);
}
