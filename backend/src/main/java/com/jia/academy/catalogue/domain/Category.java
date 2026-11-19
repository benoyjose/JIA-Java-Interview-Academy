package com.jia.academy.catalogue.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "categories")
public class Category {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="technology_id", nullable=false) private Technology technology;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="parent_id") private Category parent;
 @Column(nullable=false, unique=true, length=100) private String slug;
 @Column(nullable=false, length=160) private String name;
 @Column(name="sort_order", nullable=false) private int sortOrder;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PublicationStatus status;
 protected Category() {}
 public UUID getId(){return id;} public Technology getTechnology(){return technology;} public Category getParent(){return parent;}
 public String getSlug(){return slug;} public String getName(){return name;} public int getSortOrder(){return sortOrder;} public PublicationStatus getStatus(){return status;}
}
