package pt.tecnico.sec.server;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;


public interface ReportRepository extends CrudRepository<DBLocationReport, Integer> {

    @Query(value = "SELECT * FROM dblocation_report WHERE _epoch = ?2 and _user_id = ?1 ", nativeQuery = true)
    public DBLocationReport findReportByEpochAndUser(@Param("userId") int userId, @Param("epoch") int epoch);
}
