package hack.echo.backend.client;

import com.google.gson.*;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

public class JsonParser {

    public static byte[] serializeConfig(String config) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        Gson gson = new Gson();
        JsonElement element = gson.fromJson(config, JsonElement.class);
        JsonObject json = element.getAsJsonObject();

        JsonArray settings = json.getAsJsonArray("settings");

        dos.writeByte(0x01); // Write version
        dos.writeShort(settings.size()); // Number of settings
        for (JsonElement entry : settings) {
            JsonObject setting = entry.getAsJsonObject();
            dos.writeByte(0x02); // Setting start byte
            dos.writeUTF(setting.getAsJsonPrimitive("name").getAsString()); // Setting name
            dos.writeShort(pack(setting.getAsJsonPrimitive("enabled").getAsBoolean(), setting.getAsJsonPrimitive("keybind").getAsInt())); // Enabled and keybind
            if (!setting.has("settings")) {
                dos.writeInt(0); // No sub-settings
                continue;
            }

            JsonObject keys = setting.getAsJsonObject("settings");
            dos.writeInt(keys.size()); // Number of sub-settings
            for (String key : keys.keySet()) {
                dos.writeUTF(key); // Sub-setting name
                JsonElement value = keys.get(key);
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isBoolean()) {
                        dos.writeByte(0x01); // Type: Boolean
                        dos.writeByte(value.getAsJsonPrimitive().getAsBoolean() ? (byte) 0x01 : (byte) 0x00);
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        if (value.getAsJsonPrimitive().getAsString().contains(".")) {
                            dos.writeByte(0x03); // Type: Float
                            dos.writeFloat(value.getAsJsonPrimitive().getAsFloat());
                        } else {
                            dos.writeByte(0x02); // Type: Integer
                            dos.writeInt(value.getAsJsonPrimitive().getAsInt());
                        }
                    } else if (value.getAsJsonPrimitive().isString()) {
                        dos.writeByte(0x04); // Type: String
                        dos.writeUTF(value.getAsJsonPrimitive().getAsString());
                    }
                }
                if (value.isJsonObject()) {
                    dos.writeByte(0x05);
                    JsonObject obj = value.getAsJsonObject();
                    dos.writeShort(obj.keySet().size());
                    for (String k : obj.keySet()) {
                        dos.writeUTF(k);
                        JsonElement v = obj.get(k);
                        if (v.isJsonPrimitive()) {
                            if (v.getAsJsonPrimitive().isBoolean()) {
                                dos.writeByte(0x01); // Type: Boolean
                                dos.writeByte(v.getAsJsonPrimitive().getAsBoolean() ? (byte) 0x01 : (byte) 0x00);
                            } else if (v.getAsJsonPrimitive().isNumber()) {
                                dos.writeByte(0x02); // Type: Float
                                dos.writeFloat(v.getAsJsonPrimitive().getAsFloat());
                            } else if (v.getAsJsonPrimitive().isString()) {
                                dos.writeByte(0x03); // Type: String
                                dos.writeUTF(v.getAsJsonPrimitive().getAsString());
                            }
                        }
                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeflaterOutputStream defl = new DeflaterOutputStream(out);
        defl.write(baos.toByteArray());
        defl.flush();
        defl.close();

        return out.toByteArray();
    }

    public static String deserializeConfig(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InflaterOutputStream infl = new InflaterOutputStream(out);
        infl.write(data);
        infl.flush();
        infl.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(out.toByteArray());
        DataInputStream dis = new DataInputStream(bais);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json = new JsonObject();
        JsonArray settings = new JsonArray();
        byte version = dis.readByte(); // Read version
        if (version != 0x01) {
            throw new IOException("Unsupported config version: " + version);
        }
        int settingCount = dis.readShort(); // Number of settings
        for (int i = 0; i < settingCount; i++) {
            byte startByte = dis.readByte(); // Setting start byte
            if (startByte != 0x02) {
                throw new IOException("Invalid setting start byte: " + startByte);
            }
            JsonObject setting = new JsonObject();
            setting.addProperty("name", dis.readUTF()); // Setting name
            short packed = dis.readShort();
            setting.addProperty("enabled", unpackFlag(packed)); // Enabled
            setting.addProperty("keybind", unpackKey(packed)); // Keybind
            int subSettingCount = dis.readInt(); // Number of sub-settings
            if (subSettingCount > 0) {
                JsonObject subSettings = new JsonObject();
                for (int j = 0; j < subSettingCount; j++) {
                    String key = dis.readUTF(); // Sub-setting name
                    byte type = dis.readByte(); // Sub-setting type
                    switch (type) {
                        case 0x01 -> subSettings.addProperty(key, dis.readByte() != 0); // Boolean
                        case 0x02 -> subSettings.addProperty(key, dis.readInt()); // Integer
                        case 0x03 -> subSettings.addProperty(key, dis.readFloat()); // Float
                        case 0x04 -> subSettings.addProperty(key, dis.readUTF()); // String
                        case 0x05 -> { // Object
                            JsonObject obj = new JsonObject();
                            int objSize = dis.readShort();
                            for (int k = 0; k < objSize; k++) {
                                String objKey = dis.readUTF(); // Object key
                                byte objType = dis.readByte(); // Object value type
                                switch (objType) {
                                    case 0x01 -> obj.addProperty(objKey, dis.readByte() != 0); // Boolean
                                    case 0x02 -> obj.addProperty(objKey, dis.readFloat()); // Float
                                    case 0x03 -> obj.addProperty(objKey, dis.readUTF()); // String
                                }
                            }
                            subSettings.add(key, obj);
                        }
                        default -> throw new IOException("Unknown sub-setting type: " + type);
                    }
                }
                setting.add("settings", subSettings);
            }
            settings.add(setting);
        }
        json.add("settings", settings);
        return gson.toJson(json);
    }

    public static byte[] serializeFeatures(String features) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        Gson gson = new Gson();
        JsonElement element = gson.fromJson(features, JsonElement.class);
        JsonObject json = element.getAsJsonObject();
        JsonArray stringsArr = json.getAsJsonArray("globalStrings");

        dos.writeByte(0x01); // Write version
        dos.writeShort(stringsArr.size()); // Number of features
        int i = 0;
        for (JsonElement entry : stringsArr) {
            String string = entry.getAsString();
            int stringSeed = 0xEC40 + i * 7919;
            byte[] result = new byte[string.length()];
            for (int j = 0; j < string.length(); j++) {
                result[j] = (byte) ((string.charAt(j) & 0xFF) ^ (stringSeed & 0xFF));
                stringSeed = stringSeed * 1103515245 + 12345;
            }
            dos.writeShort(result.length);
            dos.write(result);
            i++;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeflaterOutputStream defl = new DeflaterOutputStream(out);
        defl.write(baos.toByteArray());
        defl.flush();
        defl.close();

        return out.toByteArray();
    }

    public static String deserializeFeatures(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InflaterOutputStream infl = new InflaterOutputStream(out);
        infl.write(data);
        infl.flush();
        infl.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(out.toByteArray());
        DataInputStream dis = new DataInputStream(bais);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json = new JsonObject();
        byte version = dis.readByte(); // Read version
        if (version != 0x01) {
            throw new IOException("Unsupported features version: " + version);
        }
        int featureCount = dis.readShort(); // Number of features
        JsonArray features = new JsonArray();
        for (int i = 0; i < featureCount; i++) {
            String feature = dis.readUTF(); // Feature name
            features.add(feature);
        }
        json.add("globalStrings", features);
        return gson.toJson(json);
    }

    private static short pack(boolean flag, int key) {
        int noneBit = (key == -1) ? 0x4000 : 0;
        int keyBits = (key == -1) ? 0 : (key & 0x3FFF);
        return (short) ((flag ? 0x8000 : 0) | noneBit | keyBits);
    }

    private static boolean unpackFlag(short packed) {
        return (packed & 0x8000) != 0;
    }

    private static int unpackKey(short packed) {
        return (packed & 0x4000) != 0 ? -1 : (packed & 0x3FFF);
    }
}
