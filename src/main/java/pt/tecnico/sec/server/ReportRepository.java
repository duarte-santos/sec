package pt.tecnico.sec.server;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ReportRepository extends CrudRepository<DBLocationReport, Integer> {

    @Query(value = "SELECT * FROM dblocation_report WHERE _epoch = ?2 and _user_id = ?1 ", nativeQuery = true)
    public DBLocationReport findReportByEpochAndUser(@Param("userId") int userId, @Param("epoch") int epoch);

    @Query(value = "SELECT * FROM dblocation_report JOIN dblocation ON dblocation.id = dblocation_report._db_location_id WHERE _epoch = ?1 AND _x = ?2 AND _y = ?3", nativeQuery = true)
    public List<DBLocationReport> findUsersByLocationAndEpoch(@Param("epoch") int epoch, @Param("x") int x, @Param("y") int y);

}
