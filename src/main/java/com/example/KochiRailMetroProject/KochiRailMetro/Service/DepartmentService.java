package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DepartmentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public DepartmentDto getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        return convertToDto(department);
    }

    public DepartmentDto createDepartment(DepartmentDto departmentDto) {
        if (departmentRepository.existsByCode(departmentDto.getCode())) {
            throw new RuntimeException("Department with code already exists");
        }

        Department department = new Department();
        department.setName(departmentDto.getName());
        department.setCode(departmentDto.getCode());
        department.setDescription(departmentDto.getDescription());
        department.setHeadEmail(departmentDto.getHeadEmail());
        department.setNotificationEnabled(departmentDto.getNotificationEnabled());

        department = departmentRepository.save(department);
        return convertToDto(department);
    }

    private DepartmentDto convertToDto(Department department) {
        DepartmentDto dto = new DepartmentDto();
        dto.setId(department.getId());
        dto.setName(department.getName());
        dto.setCode(department.getCode());
        dto.setDescription(department.getDescription());
        dto.setHeadEmail(department.getHeadEmail());
        dto.setNotificationEnabled(department.getNotificationEnabled());
        dto.setCreatedAt(department.getCreatedAt());
        return dto;
    }
}
