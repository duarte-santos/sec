package pt.tecnico.sec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.healthauthority.UsersAtLocation;
import pt.tecnico.sec.server.BroadcastMessage;

import java.io.IOException;
import java.util.List;

public final class ObjectMapperHandler {

    /* ========================================================== */
    /* ====[                Write as Bytes                  ]==== */
    /* ========================================================== */

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

    public static byte[] writeValueAsBytes(BroadcastMessage m) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(m);
    }


    /* ========================================================== */
    /* ====[                Get from Bytes                  ]==== */
    /* ========================================================== */

    public static String getStringFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, String.class);
    }

    public static List<LocationProof> getLocationProofListFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, new TypeReference<>(){});
    }

    public static BroadcastMessage getBroadcastMessageFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, BroadcastMessage.class);
    }

}
