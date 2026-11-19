package com.jia.academy.catalogue.repository;
import com.jia.academy.catalogue.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonRepository extends JpaRepository<Lesson, UUID> {
 List<Lesson> findAllByCategorySlugAndStatusOrderByTitleAsc(String categorySlug, PublicationStatus status);
}
