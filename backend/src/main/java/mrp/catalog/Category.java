package mrp.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 150) private String name;
    @Column(columnDefinition = "text") private String description;
    protected Category() { }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public void update(CatalogRequests.CategoryRequest r) { name=r.name().trim(); description=r.description(); }
}