package com.jia.academy.catalogue.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity @Table(name = "lessons")
public class Lesson {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="category_id", nullable=false) private Category category;
 @Column(nullable=false, unique=true, length=120) private String slug;
 @Column(nullable=false, length=200) private String title;
 @Column(length=700) private String summary;
 @Column(nullable=false, length=20) private String difficulty;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="content_json", columnDefinition="jsonb") private String contentJson;
 @Column(name="content_revision", nullable=false) private int contentRevision;
 @Column(name="source_type", nullable=false, length=30) private String sourceType;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PublicationStatus status;
 protected Lesson() {}
 public UUID getId(){return id;} public Category getCategory(){return category;} public String getSlug(){return slug;}
 public String getTitle(){return title;} public String getSummary(){return summary;} public String getDifficulty(){return difficulty;}
 public String getContentJson(){return contentJson;} public int getContentRevision(){return contentRevision;} public String getSourceType(){return sourceType;} public PublicationStatus getStatus(){return status;}
}
