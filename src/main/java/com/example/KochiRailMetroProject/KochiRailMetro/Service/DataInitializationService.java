package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Role;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.RoleRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.Set;

@Service
@Transactional
public class DataInitializationService {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void initializeData() {
        initializeRoles();
        initializeDepartments();
        createDefaultAdmin();
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("System Administrator");
            roleRepository.save(adminRole);

            Role managerRole = new Role();
            managerRole.setName("MANAGER");
            managerRole.setDescription("Department Manager");
            roleRepository.save(managerRole);

            Role employeeRole = new Role();
            employeeRole.setName("EMPLOYEE");
            employeeRole.setDescription("Department Employee");
            roleRepository.save(employeeRole);

            System.out.println("Roles initialized successfully");
        }
    }

    private void initializeDepartments() {
        if (departmentRepository.count() == 0) {
            // Operations & Maintenance
            Department operations = new Department();
            operations.setName("Operations & Maintenance");
            operations.setCode("OPS");
            operations.setDescription("Manages daily train services, station operations, and maintenance of the metro system");
            operations.setNotificationEnabled(true);
            departmentRepository.save(operations);

            // Projects & Engineering
            Department engineering = new Department();
            engineering.setName("Projects & Engineering");
            engineering.setCode("ENG");
            engineering.setDescription("Oversees the planning, design, and construction of new metro lines and extensions");
            engineering.setNotificationEnabled(true);
            departmentRepository.save(engineering);

            // Finance & Accounts
            Department finance = new Department();
            finance.setName("Finance & Accounts");
            finance.setCode("FIN");
            finance.setDescription("Handles budgeting, financial planning, and financial management of the organization");
            finance.setNotificationEnabled(true);
            departmentRepository.save(finance);

            // Human Resources
            Department hr = new Department();
            hr.setName("Human Resources");
            hr.setCode("HR");
            hr.setDescription("Manages recruitment, employee relations, and all aspects of personnel");
            hr.setNotificationEnabled(true);
            departmentRepository.save(hr);

            // Planning & Development
            Department planning = new Department();
            planning.setName("Planning & Development");
            planning.setCode("PLN");
            planning.setDescription("Responsible for long-term strategic planning, urban development integration, and future project planning");
            planning.setNotificationEnabled(true);
            departmentRepository.save(planning);

            // Procurement
            Department procurement = new Department();
            procurement.setName("Procurement");
            procurement.setCode("PRO");
            procurement.setDescription("Manages the purchasing of goods and services required for the company's operations");
            procurement.setNotificationEnabled(true);
            departmentRepository.save(procurement);

            // Information Technology
            Department it = new Department();
            it.setName("Information Technology");
            it.setCode("IT");
            it.setDescription("Manages the IT infrastructure and systems essential for running the metro");
            it.setNotificationEnabled(true);
            departmentRepository.save(it);

            System.out.println("Departments initialized successfully");
        }
    }

    private void createDefaultAdmin() {
        if (userRepository.count() == 0) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("Admin role not found"));

            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@kmrl.com");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setFullName("System Administrator");
            admin.setPhoneNumber("9876543210");
            admin.setEmployeeId("ADM-SYS-0001");
            admin.setRoles(Set.of(adminRole));
            admin.setIsActive(true);
            admin.setNotificationPreferences("EMAIL,SYSTEM");

            userRepository.save(admin);
            System.out.println("Default admin created successfully - Username: admin, Password: Admin@123");
        }
    }
}