package pt.tecnico.sec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.healthauthority.UsersAtLocation;
import pt.tecnico.sec.server.BroadcastRead;
import pt.tecnico.sec.server.BroadcastWrite;
import pt.tecnico.sec.server.DBLocationReport;

import java.io.IOException;
import java.util.List;

public final class ObjectMapperHandler {

    /* ========================================================== */
    /* ====[                Write as Bytes                  ]==== */
    /* ========================================================== */

    public static byte[] writeValueAsBytes(Integer integer) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(integer);
    }

    public static byte[] writeValueAsBytes(String string) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(string);
    }

    public static byte[] writeValueAsBytes(ProofData proofData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(proofData);
    }

    public static byte[] writeValueAsBytes(List<LocationProof> proofs) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(proofs);
    }

    public static byte[] writeValueAsBytes(LocationReport report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(report);
    }

    public static byte[] writeValueAsBytes(SignedLocationReport signedReport) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(signedReport);
    }

    public static byte[] writeValueAsBytes(DBLocationReport dbReport) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(dbReport);
    }

    public static byte[] writeValueAsBytes(UsersAtLocation users) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(users);
    }

    public static byte[] writeValueAsBytes(ObtainLocationRequest locationRequest) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(locationRequest);
    }

    public static byte[] writeValueAsBytes(ObtainUsersRequest usersRequest) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(usersRequest);
    }

    public static byte[] writeValueAsBytes(WitnessProofsRequest proofsRequest) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(proofsRequest);
    }

    public static byte[] writeValueAsBytes(SecureMessage message) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(message);
    }

    public static byte[] writeValueAsBytes(BroadcastWrite bw) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(bw);
    }

    public static byte[] writeValueAsBytes(BroadcastRead br) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(br);
    }


    /* ========================================================== */
    /* ====[                Get from Bytes                  ]==== */
    /* ========================================================== */

    public static int getIntFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, Integer.class);
    }

    public static String getStringFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, String.class);
    }

    public static List<LocationProof> getLocationProofListFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, new TypeReference<>(){});
    }

    public static SecureMessage getSecureMessageFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, SecureMessage.class);
    }

    public static BroadcastWrite getBroadcastWriteFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, BroadcastWrite.class);
    }

    public static BroadcastRead getBroadcastReadFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, BroadcastRead.class);
    }

    public static DBLocationReport getDBLocationReportFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, DBLocationReport.class);
    }

}
