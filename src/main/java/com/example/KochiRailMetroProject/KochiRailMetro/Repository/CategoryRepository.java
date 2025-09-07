package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

//import java.lang.ScopedValue;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    List<Category> findByParentIsNull();
    List<Category> findByParentId(Long parentId);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.name")
    List<Category> findRootCategories();


//    <T> ScopedValue<T> findById(Long categoryId);
}
