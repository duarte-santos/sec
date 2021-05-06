package pt.tecnico.sec.server;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ReportRepository extends CrudRepository<DBLocationReport, Integer> {

    @Query(value = "SELECT * FROM dblocation_report WHERE _epoch = ?2 and _user_id = ?1 ", nativeQuery = true)
    DBLocationReport findReportByEpochAndUser(@Param("userId") int userId, @Param("epoch") int epoch);

    @Query(value = "SELECT * FROM dblocation_report JOIN dblocation ON dblocation.id = dblocation_report._db_location_id WHERE _epoch = ?1 AND _x = ?2 AND _y = ?3", nativeQuery = true)
    List<DBLocationReport> findUsersByLocationAndEpoch(@Param("epoch") int epoch, @Param("x") int x, @Param("y") int y);

    @Query(value = "SELECT * FROM dblocation_report JOIN dblocation_report__db_proofs ON dblocation_report__db_proofs.dblocation_report_id = dblocation_report.id JOIN dblocation_proof ON dblocation_proof.id = dblocation_report__db_proofs._db_proofs_id JOIN dbproof_data ON dbproof_data.id = dblocation_proof._proof_data_id WHERE dblocation_report._epoch = ?2 and _witness_id = ?1 ", nativeQuery = true)
    List<DBLocationReport> findReportsByEpochAndWitness(@Param("witnessId") int witnessId, @Param("epoch") int epoch);

}
