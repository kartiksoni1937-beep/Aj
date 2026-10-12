package hack.echo.backend.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketUtil {

    public static byte[] decode(InputStream inputStream) throws IOException {
        byte[] header = new byte[2];
        if (inputStream.read(header) == -1) {
            //System.out.println("Returning null because I'm a selfish little fuck");
            return null;
        }

        boolean masked = (header[1] & 0x80) != 0;
        int payloadLength = header[1] & 0x7F;

        if (payloadLength == 126) {
            byte[] extendedPayloadLength = new byte[2];
            if (inputStream.read(extendedPayloadLength) != 2) {
                throw new IOException("Invalid WebSocket frame: could not read extended payload length");
            }
            payloadLength = ((extendedPayloadLength[0] & 0xFF) << 8) | (extendedPayloadLength[1] & 0xFF);
        } else if (payloadLength == 127) {
            byte[] extendedPayloadLength = new byte[4];
            if (inputStream.read(extendedPayloadLength) != 4) {
                throw new IOException("Invalid WebSocket frame: could not read extended payload length");
            }

            payloadLength = (int) ((extendedPayloadLength[0] & 0xFFL) << 24
                    | (extendedPayloadLength[1] & 0xFFL) << 16
                    | (extendedPayloadLength[2] & 0xFFL) << 8
                    | (extendedPayloadLength[3] & 0xFFL));
        }

        byte[] maskingKey = new byte[4];
        if (masked && inputStream.read(maskingKey) != 4) {
            throw new IOException("Invalid WebSocket frame: could not read masking key");
        }

        byte[] payloadData = new byte[payloadLength];
        int bytesRead = 0;
        while (bytesRead < payloadLength) {
            int result = inputStream.read(payloadData, bytesRead, payloadLength - bytesRead);
            if (result == -1) {
                break;
            }
            bytesRead += result;
        }

        if (bytesRead != payloadLength) {
            return null;
        }

        if (masked) {
            for (int i = 0; i < payloadData.length; i++) {
                payloadData[i] ^= maskingKey[i % 4];
            }
        }

        return payloadData;
    }

    public static byte[] decode(byte[] input) throws IOException {
        if (input == null || input.length < 2) {
            return null;
        }

        int index = 1;
        byte b1 = input[index++];

        boolean masked = (b1 & 0x80) != 0;
        int payloadLength = b1 & 0x7F;

        if (payloadLength == 126) {
            if (input.length < index + 2) {
                throw new IOException("Invalid WebSocket frame: could not read extended payload length (126)");
            }
            payloadLength = ((input[index] & 0xFF) << 8) | (input[index + 1] & 0xFF);
            index += 2;
        } else if (payloadLength == 127) {
            if (input.length < index + 4) {
                throw new IOException("Invalid WebSocket frame: could not read extended payload length (127)");
            }
            payloadLength = ((input[index] & 0xFF) << 24)
                    | ((input[index + 1] & 0xFF) << 16)
                    | ((input[index + 2] & 0xFF) << 8)
                    |  (input[index + 3] & 0xFF);
            index += 4;
        }

        byte[] maskingKey = null;
        if (masked) {
            if (input.length < index + 4) {
                throw new IOException("Invalid WebSocket frame: could not read masking key");
            }
            maskingKey = Arrays.copyOfRange(input, index, index + 4);
            index += 4;
        }

        if (input.length < index + payloadLength) {
            return null;
        }

        byte[] payloadData = Arrays.copyOfRange(input, index, index + payloadLength);

        if (masked) {
            for (int i = 0; i < payloadData.length; i++) {
                payloadData[i] ^= maskingKey[i % 4];
            }
        }

        return payloadData;
    }

    public static byte[] encode(byte[] rawData) {
        int frameCount;
        byte[] frame = new byte[6];

        frame[0] = (byte) 129;

        if (rawData.length <= 125) {
            frame[1] = (byte) rawData.length;
            frameCount = 2;
        } else if (rawData.length <= 65535) {
            int len = rawData.length;
            frame[1] = 126;
            frame[2] = (byte)((len >> 8) & 0xFF);
            frame[3] = (byte)(len & 0xFF);
            frameCount = 4;
        } else {
            int len = rawData.length;
            frame[1] = 127;
            frame[2] = (byte)((len >> 24) & 0xFF);
            frame[3] = (byte)((len >> 16) & 0xFF);
            frame[4] = (byte)((len >> 8) & 0xFF);
            frame[5] = (byte)(len & 0xFF);
            frameCount = 6;
        }

        int bLength = frameCount + rawData.length;

        byte[] reply = new byte[bLength];

        int bLim = 0;
        for (int i = 0; i < frameCount; i++) {
            reply[bLim] = frame[i];
            bLim++;
        }
        for (byte rawDatum : rawData) {
            reply[bLim] = rawDatum;
            bLim++;
        }

        return reply;
    }

    public static Response handshake(InputStream input, OutputStream output) {
        try {
            StringBuilder headerBuilder = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;
            boolean headersComplete = false;

            while (!headersComplete && (bytesRead = input.read(buffer)) != -1) {
                headerBuilder.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                if (headerBuilder.toString().contains("\r\n\r\n")) {
                    headersComplete = true;
                }
                String clientIp = extractClientIp(headerBuilder.toString());
                if (headerBuilder.length() > 8192) {
                    sendError(output, 400);
                    return new Response(false, clientIp);
                }
            }

            String clientIp = extractClientIp(headerBuilder.toString());
            if (!headersComplete) {
                return new Response(false, clientIp);
            }

            String data = headerBuilder.toString();
            //System.out.println("Received request:\n" + data); // Debug logging

            if (!data.startsWith("GET")) {
                sendError(output, 405);
                return new Response(false, clientIp);
            }

            Matcher match = Pattern.compile("Sec-Websocket-Key: ([a-zA-Z0-9+/=]+)").matcher(data);
            if (!match.find()) {
                sendError(output, 400);
                return new Response(false, clientIp);
            }

            String clientKey = match.group(1).trim();
            String magic = clientKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(magic.getBytes(StandardCharsets.UTF_8));
            String acceptKey = Base64.getEncoder().encodeToString(hash);

            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 101 Switching Protocols\r\n");
            response.append("Upgrade: websocket\r\n");
            response.append("Connection: Upgrade\r\n");
            response.append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n");
            response.append("Sec-WebSocket-Version: 13\r\n");
            response.append("\r\n");

            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            output.write(responseBytes);
            output.flush();

            //System.out.println("Handshake complete, sent:\n" + response);
            return new Response(true, clientIp);
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "unknown");
        }
    }

    private static String extractClientIp(String headers) {
        Matcher cfMatch = Pattern.compile("(?i)CF-Connecting-IP: ([\\d.]+|[0-9a-fA-F:]+)").matcher(headers);
        if (cfMatch.find()) {
            return cfMatch.group(1).trim();
        }

        Matcher xffMatch = Pattern.compile("(?i)X-Forwarded-For: ([\\d.]+|[0-9a-fA-F:]+)").matcher(headers);
        if (xffMatch.find()) {
            String full = xffMatch.group(1).trim();
            return full.split(",")[0].trim();
        }

        return "unknown";
    }

    private static void sendError(OutputStream output, int code) {
        try {
            String response = "HTTP/1.1 " + code + "\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException ignored) {}
    }

    public static class Response {

        public boolean success;
        public String ip;

        public Response(boolean success, String ip) {
            this.success = success;
            this.ip = ip;
        }
    }
}
