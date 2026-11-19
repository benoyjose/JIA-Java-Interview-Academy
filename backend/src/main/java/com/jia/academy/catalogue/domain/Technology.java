package com.jia.academy.catalogue.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "technologies")
public class Technology {
 @Id private UUID id;
 @Column(nullable=false, unique=true, length=80) private String slug;
 @Column(nullable=false, length=140) private String name;
 @Column(length=500) private String description;
 @Column(name="sort_order", nullable=false) private int sortOrder;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PublicationStatus status;
 protected Technology() {}
 public UUID getId(){return id;} public String getSlug(){return slug;} public String getName(){return name;}
 public String getDescription(){return description;} public int getSortOrder(){return sortOrder;} public PublicationStatus getStatus(){return status;}
}
