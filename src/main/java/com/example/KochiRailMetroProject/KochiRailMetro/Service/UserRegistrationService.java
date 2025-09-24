package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserRegistrationDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Role;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Exception.BadRequestException;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.RoleRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   DepartmentRepository departmentRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------- Register Users ----------------

    public UserDto registerManager(UserRegistrationDto dto, UserPrincipal currentUser) {
        if (!hasRole(currentUser, "ADMIN")) {
            throw new RuntimeException("Only admins can register managers");
        }
        validateUniqueUser(dto);
        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // Check if manager exists for this department
        List<User> existingManagers = userRepository.findEmployeesByDepartment(department).stream()
                .filter(u -> hasUserRole(u, "MANAGER"))
                .collect(Collectors.toList());
        if (!existingManagers.isEmpty()) {
            throw new BadRequestException("Department already has a manager assigned");
        }

        User manager = createUser(dto, "MANAGER", department);
        manager.setEmployeeId(generateEmployeeId("MGR", department.getCode(), department));
        User savedUser = userRepository.save(manager);
        return convertToDto(savedUser);
    }

    public UserDto registerEmployee(UserRegistrationDto dto, UserPrincipal currentUser) {
        User manager = userRepository.findByIdWithDepartment(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        Department managerDept = manager.getDepartment();
        if (managerDept == null) {
            throw new RuntimeException("Manager has no department assigned");
        }

        User employee = new User();
        employee.setUsername(dto.getUsername());
        employee.setEmail(dto.getEmail());
        employee.setFullName(dto.getFullName());
        employee.setPassword(passwordEncoder.encode(dto.getPassword()));
        employee.setPhoneNumber(dto.getPhoneNumber());
        employee.setNotificationPreferences(dto.getNotificationPreferences());
        employee.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        employee.setDepartment(managerDept);

        Role employeeRole = roleRepository.findByName("EMPLOYEE")
                .orElseThrow(() -> new RuntimeException("Role not found"));
        employee.setRoles(Set.of(employeeRole));

        employee.setEmployeeId(generateEmployeeId("EMP", managerDept.getCode(), managerDept));

        User savedEmployee = userRepository.save(employee);
        return convertToDto(savedEmployee);
    }

    // ---------------- Fetch Users ----------------

    public List<UserDto> getAllManagers() {
        return userRepository.findAllManagers().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllEmployees() {
        return userRepository.findAllEmployees().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getEmployeesByManager(UserPrincipal currentUser) {
        User manager = userRepository.findByIdWithDepartment(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        Department dept = manager.getDepartment();
        if (dept == null) {
            throw new RuntimeException("Manager has no department assigned");
        }
        return userRepository.findEmployeesByDepartment(dept).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public UserDto getUserProfile(UserPrincipal currentUser) {
        User user = userRepository.findByIdWithDepartment(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    // ---------------- Update / Delete ----------------

    public UserDto updateUser(Long userId, UserRegistrationDto dto, UserPrincipal currentUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFullName(dto.getFullName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setEmail(dto.getEmail());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setNotificationPreferences(dto.getNotificationPreferences());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : user.getIsActive());
        userRepository.save(user);
        return convertToDto(user);
    }

    public void deleteManager(Long userId, UserPrincipal currentUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        if (!hasUserRole(user, "MANAGER")) {
            throw new RuntimeException("User is not a manager");
        }
        userRepository.delete(user);
    }

    public void deleteEmployee(Long userId, UserPrincipal currentUser) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (!hasUserRole(employee, "EMPLOYEE")) {
            throw new RuntimeException("User is not an employee");
        }
        userRepository.delete(employee);
    }

    public UserDto updateOwnProfile(UserRegistrationDto dto, UserPrincipal currentUser) {
        User user = userRepository.findByIdWithDepartment(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFullName(dto.getFullName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setEmail(dto.getEmail());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setNotificationPreferences(dto.getNotificationPreferences());
        userRepository.save(user);
        return convertToDto(user);
    }

    // ---------------- Helper Methods ----------------

    private User createUser(UserRegistrationDto dto, String roleName, Department department) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setFullName(dto.getFullName());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setDepartment(department);
        user.setNotificationPreferences(dto.getNotificationPreferences());
        user.setIsActive(true);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException(roleName + " role not found"));
        user.setRoles(Set.of(role));
        return user;
    }

    private void validateUniqueUser(UserRegistrationDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
    }

    private String generateEmployeeId(String prefix, String departmentCode, Department department) {
        long count = userRepository.countEmployeesInDepartment(department) + 1;
        return String.format("%s-%s-%03d", prefix, departmentCode, count);
    }

    private boolean hasRole(UserPrincipal userPrincipal, String roleName) {
        return userPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + roleName));
    }

    private boolean hasUserRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals(roleName));
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setIsActive(user.getIsActive());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setNotificationPreferences(user.getNotificationPreferences());
        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
            dto.setDepartmentName(user.getDepartment().getName());
            dto.setDepartmentCode(user.getDepartment().getCode());
        }
        dto.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));

        // Set manager info if employee
        if (hasUserRole(user, "EMPLOYEE") && user.getDepartment() != null) {
            userRepository.findEmployeesByDepartment(user.getDepartment()).stream()
                    .filter(u -> hasUserRole(u, "MANAGER"))
                    .findFirst()
                    .ifPresent(manager -> {
                        dto.setManagerName(manager.getFullName());
                        dto.setManagerEmail(manager.getEmail());
                    });
        }
        return dto;
    }
}
