package pt.tecnico.sec.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.report.LocationReport;
import pt.tecnico.sec.client.report.ProofData;
import pt.tecnico.sec.server.broadcast.BroadcastMessage;

import java.io.IOException;

@SuppressWarnings("deprecation")
public final class ObjectMapperHandler {

    /* ========================================================== */
    /* ====[                Write as Bytes                  ]==== */
    /* ========================================================== */

    public static byte[] writeValueAsBytes(ProofData proofData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(proofData);
    }

    public static byte[] writeValueAsBytes(LocationReport report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(report);
    }

    public static byte[] writeValueAsBytes(BroadcastMessage m) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(m);
    }

    public static byte[] writeValueAsBytes(Message m) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTyping();
        return objectMapper.writeValueAsBytes(m);
    }


    /* ========================================================== */
    /* ====[                Get from Bytes                  ]==== */
    /* ========================================================== */

    public static BroadcastMessage getBroadcastMessageFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, BroadcastMessage.class);
    }

    public static Message getMessageFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTyping();
        return objectMapper.readValue(bytes, Message.class);
    }
}
