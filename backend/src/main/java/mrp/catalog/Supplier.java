package mrp.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 20) private String phone;
    @Column(length = 150) private String email;
    @Column(columnDefinition = "text") private String address;
    protected Supplier() { }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public void update(CatalogRequests.SupplierRequest r) { name=r.name().trim(); phone=r.phone(); email=r.email(); address=r.address(); }
}