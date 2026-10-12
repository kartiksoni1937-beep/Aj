package hack.echo.backend.util;

public class FlagUtil {

    public static String resolve(byte flag) {
        return switch (flag) {
            case 0 -> "Username mismatch on login";
            case 1 -> "Password salt length mismatch on login";
            case 2 -> "Password mismatch on login";
            case 3 -> "HWID mismatch on login";
            case 4 -> "Invalid timestamp on login";
            case 5 -> "Failed banned check on login";
            case 6 -> "Invalid server packet id";
            case 7 -> "Handshake key mismatch";
            case 8 -> "Handshake timestamp exceeds 60000ms";
            case 9 -> "invalid encryption IV position";
            case 10 -> "Invalid packet header (126)";
            case 11 -> "Invalid packet header (127)";
            case 14 -> "Invalid base64 length on handshake";
            case 15 -> "Invalid handshake length";
            case 16 -> "Invalid digit in handshake";
            case 17 -> "Failed handshake calculation (1)";
            case 18 -> "Failed handshake calculation (2)";
            case 19 -> "Invalid initial keep alive value";
            case 20 -> "No session token on reconnect";
            case 21 -> "Failed base64 decoding on handshake (1)";
            case 22 -> "Failed base64 decoding on handshake (2)";
            case 23 -> "Invalid websocket connection header";
            case 24 -> "Previous keep alive matches current";
            case 25 -> "Previous keep alive key matches current";
            case 26 -> "Invalid keep alive";
            case 27 -> "Trying to process client packet before load";
            case 28 -> "Invalid client reinitialization attempt";
            case 29 -> "Invalid code segment";
            case 30 -> "Features version mismatch";
            case 31 -> "Invalid decryption IV position";
            default -> "Unknown";
        };
    }
}
