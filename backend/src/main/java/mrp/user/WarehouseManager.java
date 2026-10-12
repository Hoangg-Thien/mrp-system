package mrp.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "WarehouseManager")
@Table(name = "warehouse_managers")
public class WarehouseManager {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Column(name = "full_name", nullable = false, length = 150) private String fullName;
    @Column(length = 150) private String email;
    @Column(length = 20) private String phone;
    private String address;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private UserRole role = UserRole.WAREHOUSE_MANAGER;
    protected WarehouseManager() { }
    public WarehouseManager(Long userId, String fullName, String email, String phone, String address) { this.userId=userId; this.fullName=fullName; this.email=email; this.phone=phone; this.address=address; }
    public Long getId(){return id;} public Long getUserId(){return userId;} public String getFullName(){return fullName;} public String getEmail(){return email;} public String getPhone(){return phone;} public String getAddress(){return address;}
}