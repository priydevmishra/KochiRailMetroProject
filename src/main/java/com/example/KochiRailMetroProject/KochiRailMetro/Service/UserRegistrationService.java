package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserRegistrationDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Role;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.RoleRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
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

    public UserDto registerManager(UserRegistrationDto registrationDto, UserPrincipal currentUser) {
        // Validate that current user is admin
        if (!hasRole(currentUser, "ADMIN")) {
            throw new RuntimeException("Only admins can register managers");
        }

        // Check if username or email already exists
        validateUniqueUser(registrationDto);

        // Get department
        Department department = departmentRepository.findById(registrationDto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // Check if department already has a manager
        List<User> existingManagers = userRepository.findByDepartment(department)
                .stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("MANAGER")))
                .collect(Collectors.toList());

        if (!existingManagers.isEmpty()) {
            throw new RuntimeException("Department already has a manager assigned");
        }

        User user = createUser(registrationDto, "MANAGER", department);
        user.setEmployeeId(generateEmployeeId("MGR", department.getCode()));

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    public UserDto registerEmployee(UserRegistrationDto registrationDto, UserPrincipal currentUser) {
        // Validate that current user is manager
        if (!hasRole(currentUser, "MANAGER")) {
            throw new RuntimeException("Only managers can register employees");
        }

        // Get current user and their department
        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        Department department = manager.getDepartment();
        if (department == null) {
            throw new RuntimeException("Manager must be assigned to a department");
        }

        // Ensure employee is being registered in manager's department
        if (!registrationDto.getDepartmentId().equals(department.getId())) {
            throw new RuntimeException("Managers can only register employees in their own department");
        }

        // Check if username or email already exists
        validateUniqueUser(registrationDto);

        User user = createUser(registrationDto, "EMPLOYEE", department);
        user.setEmployeeId(generateEmployeeId("EMP", department.getCode()));

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    public UserDto updateUser(Long userId, UserRegistrationDto updateDto, UserPrincipal currentUser) {
        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check permissions
        if (!canUpdateUser(currentUser, userToUpdate)) {
            throw new RuntimeException("Insufficient permissions to update this user");
        }

        // Update allowed fields
        userToUpdate.setFullName(updateDto.getFullName());
        userToUpdate.setPhoneNumber(updateDto.getPhoneNumber());
        userToUpdate.setNotificationPreferences(updateDto.getNotificationPreferences());

        // Admin and managers can update more fields
        if (hasRole(currentUser, "ADMIN") || hasRole(currentUser, "MANAGER")) {
            if (updateDto.getEmail() != null && !updateDto.getEmail().equals(userToUpdate.getEmail())) {
                if (userRepository.existsByEmail(updateDto.getEmail())) {
                    throw new RuntimeException("Email already exists");
                }
                userToUpdate.setEmail(updateDto.getEmail());
            }

            if (updateDto.getIsActive() != null) {
                userToUpdate.setIsActive(updateDto.getIsActive());
            }
        }

        User savedUser = userRepository.save(userToUpdate);
        return convertToDto(savedUser);
    }

    public UserDto updateOwnProfile(UserRegistrationDto updateDto, UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Users can only update their own basic information
        user.setFullName(updateDto.getFullName());
        user.setPhoneNumber(updateDto.getPhoneNumber());
        user.setNotificationPreferences(updateDto.getNotificationPreferences());

        // Email update requires validation
        if (updateDto.getEmail() != null && !updateDto.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateDto.getEmail())) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(updateDto.getEmail());
        }

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    public void deleteManager(Long userId, UserPrincipal currentUser) {
        if (!hasRole(currentUser, "ADMIN")) {
            throw new RuntimeException("Only admins can delete managers");
        }

        User manager = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        if (!hasUserRole(manager, "MANAGER")) {
            throw new RuntimeException("User is not a manager");
        }

        // Check if manager has employees
        List<User> employees = userRepository.findByDepartment(manager.getDepartment())
                .stream()
                .filter(user -> hasUserRole(user, "EMPLOYEE"))
                .collect(Collectors.toList());

        if (!employees.isEmpty()) {
            throw new RuntimeException("Cannot delete manager with active employees. Please reassign employees first.");
        }

        userRepository.delete(manager);
    }

    public void deleteEmployee(Long userId, UserPrincipal currentUser) {
        if (!hasRole(currentUser, "MANAGER")) {
            throw new RuntimeException("Only managers can delete employees");
        }

        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!hasUserRole(employee, "EMPLOYEE")) {
            throw new RuntimeException("User is not an employee");
        }

        // Check if employee is in manager's department
        if (!employee.getDepartment().getId().equals(manager.getDepartment().getId())) {
            throw new RuntimeException("Can only delete employees from your own department");
        }

        userRepository.delete(employee);
    }

    public List<UserDto> getAllManagers() {
        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseThrow(() -> new RuntimeException("Manager role not found"));

        return userRepository.findAll().stream()
                .filter(user -> user.getRoles().contains(managerRole))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllEmployees() {
        Role employeeRole = roleRepository.findByName("EMPLOYEE")
                .orElseThrow(() -> new RuntimeException("Employee role not found"));

        return userRepository.findAll().stream()
                .filter(user -> user.getRoles().contains(employeeRole))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getEmployeesByManager(UserPrincipal currentUser) {
        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        if (manager.getDepartment() == null) {
            throw new RuntimeException("Manager not assigned to any department");
        }

        Role employeeRole = roleRepository.findByName("EMPLOYEE")
                .orElseThrow(() -> new RuntimeException("Employee role not found"));

        return userRepository.findByDepartment(manager.getDepartment()).stream()
                .filter(user -> user.getRoles().contains(employeeRole))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public UserDto getUserProfile(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    // Helper methods
    private User createUser(UserRegistrationDto dto, String roleName, Department department) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFullName(dto.getFullName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setDepartment(department);
        user.setNotificationPreferences(dto.getNotificationPreferences());
        user.setIsActive(true);

        // Set role
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

    private String generateEmployeeId(String prefix, String departmentCode) {
        // Generate unique employee ID based on prefix, department and current timestamp
        long timestamp = System.currentTimeMillis() % 10000;
        return prefix + "-" + departmentCode + "-" + timestamp;
    }

    private boolean hasRole(UserPrincipal userPrincipal, String roleName) {
        return userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + roleName));
    }

    private boolean hasUserRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    private boolean canUpdateUser(UserPrincipal currentUser, User userToUpdate) {
        // Users can update themselves
        if (currentUser.getId().equals(userToUpdate.getId())) {
            return true;
        }

        // Admins can update managers
        if (hasRole(currentUser, "ADMIN") && hasUserRole(userToUpdate, "MANAGER")) {
            return true;
        }

        // Managers can update employees in their department
        if (hasRole(currentUser, "MANAGER") && hasUserRole(userToUpdate, "EMPLOYEE")) {
            User manager = userRepository.findById(currentUser.getId()).orElse(null);
            if (manager != null && manager.getDepartment() != null) {
                return manager.getDepartment().getId().equals(userToUpdate.getDepartment().getId());
            }
        }

        return false;
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setIsActive(user.getIsActive());
        dto.setNotificationPreferences(user.getNotificationPreferences());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
            dto.setDepartmentName(user.getDepartment().getName());
            dto.setDepartmentCode(user.getDepartment().getCode());
        }

        dto.setRoles(user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet()));

        // Set manager information for employees
        if (hasUserRole(user, "EMPLOYEE") && user.getDepartment() != null) {
            userRepository.findByDepartment(user.getDepartment()).stream()
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
