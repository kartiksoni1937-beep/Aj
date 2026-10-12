package hack.echo.backend.util;

import hack.echo.backend.packet.Packet;
import hack.echo.backend.packet.impl.client.*;
import hack.echo.backend.packet.impl.server.*;

import javax.crypto.SecretKey;
import java.io.*;
import java.util.Arrays;

public class PacketUtil {

    public static void sendPacket(DataOutputStream output, Packet packet, SecretKey key) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream oos = new DataOutputStream(bos);
        oos.writeByte(packet.getId());

        switch (packet.getId()) {
            case 0 -> {
                SHandshake handshake = (SHandshake) packet;
                oos.writeUTF(handshake.getKey());
            }
            case 1 -> {
                SKeepAlivePacket keepAlivePacket = (SKeepAlivePacket) packet;
                oos.writeLong(keepAlivePacket.getValue() ^ keepAlivePacket.getKey());
                oos.writeLong(keepAlivePacket.getKey() ^ 9012476591726549716L);
            }
            case 2 -> {
                SLoginPacket loginPacket = (SLoginPacket) packet;
                oos.writeUTF(loginPacket.getUsername());
                oos.writeUTF(loginPacket.getPassword());
                oos.writeUTF(loginPacket.getHwid());
                oos.writeUTF(loginPacket.getUid());
                oos.writeLong(loginPacket.getTimestamp());
                oos.writeUTF(loginPacket.getSessionToken());
                oos.writeBoolean(loginPacket.isBanned());
                oos.writeInt(loginPacket.getServerInteger());
                oos.writeInt(loginPacket.getSecretLocalKey());
                oos.writeInt(loginPacket.getRuntimeConstant());
            }
            case 3 -> {
                SReconnectPacket reconnectPacket = (SReconnectPacket) packet;
                oos.writeUTF(reconnectPacket.getSessionToken());
                oos.writeUTF(reconnectPacket.getHwid());
                oos.writeUTF(reconnectPacket.getUid());
                oos.writeLong(reconnectPacket.getTimestamp());
                oos.writeBoolean(reconnectPacket.isBanned());
            }
            case 5 -> {
                SJarPacket jarPacket = (SJarPacket) packet;
                oos.writeUTF(jarPacket.getKey());
                oos.writeInt(jarPacket.getData().length ^ jarPacket.getKey().hashCode());
                oos.write(jarPacket.getData());
            }
            case 6 -> {
                SConfigPacket configPacket = (SConfigPacket) packet;
                oos.writeInt(configPacket.getData().length);
                oos.write(configPacket.getData());
            }
            case 4, 7, 8 -> {}
            case 9 -> {
                SFeaturesPacket featuresPacket = (SFeaturesPacket) packet;
                oos.writeInt(featuresPacket.getData().length);
                oos.write(featuresPacket.getData());
            }
            case 10 -> {
                SKeyExchangePacket keyExchangePacket = (SKeyExchangePacket) packet;
                oos.writeUTF(keyExchangePacket.getKey());
            }
            case 11 -> {
                SBroadcastPacket broadcastPacket = (SBroadcastPacket) packet;
                oos.writeUTF(broadcastPacket.getMessage());
            }
        }

        oos.flush();
        oos.close();
        byte[] data = bos.toByteArray();
        //System.out.println("Sending data: " + Arrays.toString(data));
        //System.out.println("Sending packet: " + packet.getClass().getSimpleName() + " (id: " + packet.getId() + ", size: " + data.length + " bytes)");
        data = EncryptionUtil.encrypt(data, key);
        bos.close();

        data = WebSocketUtil.encode(data);
        output.write(data);
        output.flush();
    }

    public static Packet readPacket(DataInputStream input, SecretKey key) throws IOException {
        try {
            byte[] data = WebSocketUtil.decode(input);
            if (data != null && data.length == 2 && data[0] == 4) {
                return new CFlagPacket(data[1]);
            }
            data = EncryptionUtil.decrypt(data, key);
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            DataInputStream ois = new DataInputStream(bis);
            int id = ois.readByte();

            Packet packet = switch (id) {
                case 0 -> new CHandshake(ois.readUTF());
                case 1 -> new CKeepAlivePacket(ois.readLong());
                case 2 -> new CLoginPacket(ois.readUTF(), ois.readUTF(), ois.readUTF());
                case 3 -> new CDownloadPacket();
                case 4 -> new CFlagPacket(ois.readByte());
                case 5 -> new CReconnectPacket(ois.readUTF(), ois.readUTF());
                case 6 -> new CConfigPacket(ois.readUTF());
                case 7 -> new CSaveConfigPacket(ois.readUTF(), ois.readUTF());
                case 8 -> new CDeleteConfigPacket(ois.readUTF());
                case 9 -> new CFeaturesPacket();
                case 10 -> new CKeyExchangePacket(ois.readUTF());
                default -> new CInvalidPacket(id);
            };

            //System.out.println("Received packet: " + packet.getClass().getSimpleName() + " (id: " + packet.getId() + ", size: " + data.length + " bytes)");

            ois.close();
            bis.close();
            return packet;
        } catch (IllegalArgumentException ignored) {
            return new CInvalidPacket(-1);
        } catch (NullPointerException ignored) {
            return null;
        }
    }
}