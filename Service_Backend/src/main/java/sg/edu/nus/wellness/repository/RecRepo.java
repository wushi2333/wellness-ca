// Author: Xia Zihang
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RecRepo extends JpaRepository<Recommendation,Long> {}
