package hack.echo.auth;

/*import hack.echo.client.Echo;
import hack.echo.client.auth.AuthManager;
import hack.echo.client.auth.CentralFeatureInfoHub;
import hack.echo.client.auth.MathProt;
import hack.echo.client.chunk.BlockEspManager;
import hack.echo.client.chunk.ChunkManager;
import hack.echo.client.command.CommandManager;
import hack.echo.client.config.FeatureConfig;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureManager;
import hack.echo.client.features.impl.combat.*;
import hack.echo.client.features.impl.macros.*;
import hack.echo.client.features.impl.macros.donut.InstaAuctionSell;
import hack.echo.client.features.impl.macros.donut.InstaCrate;
import hack.echo.client.features.impl.macros.donut.InstaSell;
import hack.echo.client.features.impl.misc.*;
import hack.echo.client.features.impl.movement.*;
import hack.echo.client.features.impl.player.*;
import hack.echo.client.features.impl.render.*;
import hack.echo.client.features.impl.render.hud.*;
import hack.echo.client.friends.FriendManager;
import hack.echo.client.handlers.HandlerManager;
import hack.echo.client.handlers.impl.BroadcastHandler;
import hack.echo.client.particle.ParticleManager;
import hack.echo.client.screens.ScreenManager;
import hack.echo.client.spotify.SpotifyManager;
import hack.echo.client.utils.audio.EchoAudio;
import hack.echo.client.utils.strings.Concat;*/
import war.jnt.api.CustomArray;
import war.jnt.api.JNT;
import war.jnt.api.LimitCache;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public class Main {

    public static void main(String[] args) {
        init("test", "test", (Boolean success, byte[] sessionKey) -> {
            if (success) {
                System.out.println("Login successful");
            } else {
                System.out.println("Login failed.");
            }
            return null;
        });
    }

    public static void init(String susername, String spassword, BiFunction<Boolean, byte[], Void> callback) {
        Thread thread = new Thread(() -> lambda$init$0(susername, spassword, callback));
        //thread.setDaemon(true);
        thread.start();
    }

    @LimitCache
    @CustomArray
    private static void lambda$init$0(String susername, String spassword, BiFunction<Boolean, byte[], Void> callback) {
        try {
            //System.out.println("Asd");
            String prehwid = System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("COMPUTERNAME") + "EC" + System.getProperty("user.name");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prehwid.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String shwid = hexString.toString();

            byte[] SBOX = new byte[] {
                    (byte)0x63,(byte)0x7c,(byte)0x77,(byte)0x7b,(byte)0xf2,(byte)0x6b,(byte)0x6f,(byte)0xc5,
                    (byte)0x30,(byte)0x01,(byte)0x67,(byte)0x2b,(byte)0xfe,(byte)0xd7,(byte)0xab,(byte)0x76,
                    (byte)0xca,(byte)0x82,(byte)0xc9,(byte)0x7d,(byte)0xfa,(byte)0x59,(byte)0x47,(byte)0xf0,
                    (byte)0xad,(byte)0xd4,(byte)0xa2,(byte)0xaf,(byte)0x9c,(byte)0xa4,(byte)0x72,(byte)0xc0,
                    (byte)0xb7,(byte)0xfd,(byte)0x93,(byte)0x26,(byte)0x36,(byte)0x3f,(byte)0xf7,(byte)0xcc,
                    (byte)0x34,(byte)0xa5,(byte)0xe5,(byte)0xf1,(byte)0x71,(byte)0xd8,(byte)0x31,(byte)0x15,
                    (byte)0x04,(byte)0xc7,(byte)0x23,(byte)0xc3,(byte)0x18,(byte)0x96,(byte)0x05,(byte)0x9a,
                    (byte)0x07,(byte)0x12,(byte)0x80,(byte)0xe2,(byte)0xeb,(byte)0x27,(byte)0xb2,(byte)0x75,
                    (byte)0x09,(byte)0x83,(byte)0x2c,(byte)0x1a,(byte)0x1b,(byte)0x6e,(byte)0x5a,(byte)0xa0,
                    (byte)0x52,(byte)0x3b,(byte)0xd6,(byte)0xb3,(byte)0x29,(byte)0xe3,(byte)0x2f,(byte)0x84,
                    (byte)0x53,(byte)0xd1,(byte)0x00,(byte)0xed,(byte)0x20,(byte)0xfc,(byte)0xb1,(byte)0x5b,
                    (byte)0x6a,(byte)0xcb,(byte)0xbe,(byte)0x39,(byte)0x4a,(byte)0x4c,(byte)0x58,(byte)0xcf,
                    (byte)0xd0,(byte)0xef,(byte)0xaa,(byte)0xfb,(byte)0x43,(byte)0x4d,(byte)0x33,(byte)0x85,
                    (byte)0x45,(byte)0xf9,(byte)0x02,(byte)0x7f,(byte)0x50,(byte)0x3c,(byte)0x9f,(byte)0xa8,
                    (byte)0x51,(byte)0xa3,(byte)0x40,(byte)0x8f,(byte)0x92,(byte)0x9d,(byte)0x38,(byte)0xf5,
                    (byte)0xbc,(byte)0xb6,(byte)0xda,(byte)0x21,(byte)0x10,(byte)0xff,(byte)0xf3,(byte)0xd2,
                    (byte)0xcd,(byte)0x0c,(byte)0x13,(byte)0xec,(byte)0x5f,(byte)0x97,(byte)0x44,(byte)0x17,
                    (byte)0xc4,(byte)0xa7,(byte)0x7e,(byte)0x3d,(byte)0x64,(byte)0x5d,(byte)0x19,(byte)0x73,
                    (byte)0x60,(byte)0x81,(byte)0x4f,(byte)0xdc,(byte)0x22,(byte)0x2a,(byte)0x90,(byte)0x88,
                    (byte)0x46,(byte)0xee,(byte)0xb8,(byte)0x14,(byte)0xde,(byte)0x5e,(byte)0x0b,(byte)0xdb,
                    (byte)0xe0,(byte)0x32,(byte)0x3a,(byte)0x0a,(byte)0x49,(byte)0x06,(byte)0x24,(byte)0x5c,
                    (byte)0xc2,(byte)0xd3,(byte)0xac,(byte)0x62,(byte)0x91,(byte)0x95,(byte)0xe4,(byte)0x79,
                    (byte)0xe7,(byte)0xc8,(byte)0x37,(byte)0x6d,(byte)0x8d,(byte)0xd5,(byte)0x4e,(byte)0xa9,
                    (byte)0x6c,(byte)0x56,(byte)0xf4,(byte)0xea,(byte)0x65,(byte)0x7a,(byte)0xae,(byte)0x08,
                    (byte)0xba,(byte)0x78,(byte)0x25,(byte)0x2e,(byte)0x1c,(byte)0xa6,(byte)0xb4,(byte)0xc6,
                    (byte)0xe8,(byte)0xdd,(byte)0x74,(byte)0x1f,(byte)0x4b,(byte)0xbd,(byte)0x8b,(byte)0x8a,
                    (byte)0x70,(byte)0x3e,(byte)0xb5,(byte)0x66,(byte)0x48,(byte)0x03,(byte)0xf6,(byte)0x0e,
                    (byte)0x61,(byte)0x35,(byte)0x57,(byte)0xb9,(byte)0x86,(byte)0xc1,(byte)0x1d,(byte)0x9e,
                    (byte)0xe1,(byte)0xf8,(byte)0x98,(byte)0x11,(byte)0x69,(byte)0xd9,(byte)0x8e,(byte)0x94,
                    (byte)0x9b,(byte)0x1e,(byte)0x87,(byte)0xe9,(byte)0xce,(byte)0x55,(byte)0x28,(byte)0xdf,
                    (byte)0x8c,(byte)0xa1,(byte)0x89,(byte)0x0d,(byte)0xbf,(byte)0xe6,(byte)0x42,(byte)0x68,
                    (byte)0x41,(byte)0x99,(byte)0x2d,(byte)0x0f,(byte)0xb0,(byte)0x54,(byte)0xbb,(byte)0x16
            };

            byte[] INVERSE_SBOX = new byte[] {
                    (byte)0x52,(byte)0x09,(byte)0x6a,(byte)0xd5,(byte)0x30,(byte)0x36,(byte)0xa5,(byte)0x38,
                    (byte)0xbf,(byte)0x40,(byte)0xa3,(byte)0x9e,(byte)0x81,(byte)0xf3,(byte)0xd7,(byte)0xfb,
                    (byte)0x7c,(byte)0xe3,(byte)0x39,(byte)0x82,(byte)0x9b,(byte)0x2f,(byte)0xff,(byte)0x87,
                    (byte)0x34,(byte)0x8e,(byte)0x43,(byte)0x44,(byte)0xc4,(byte)0xde,(byte)0xe9,(byte)0xcb,
                    (byte)0x54,(byte)0x7b,(byte)0x94,(byte)0x32,(byte)0xa6,(byte)0xc2,(byte)0x23,(byte)0x3d,
                    (byte)0xee,(byte)0x4c,(byte)0x95,(byte)0x0b,(byte)0x42,(byte)0xfa,(byte)0xc3,(byte)0x4e,
                    (byte)0x08,(byte)0x2e,(byte)0xa1,(byte)0x66,(byte)0x28,(byte)0xd9,(byte)0x24,(byte)0xb2,
                    (byte)0x76,(byte)0x5b,(byte)0xa2,(byte)0x49,(byte)0x6d,(byte)0x8b,(byte)0xd1,(byte)0x25,
                    (byte)0x72,(byte)0xf8,(byte)0xf6,(byte)0x64,(byte)0x86,(byte)0x68,(byte)0x98,(byte)0x16,
                    (byte)0xd4,(byte)0xa4,(byte)0x5c,(byte)0xcc,(byte)0x5d,(byte)0x65,(byte)0xb6,(byte)0x92,
                    (byte)0x6c,(byte)0x70,(byte)0x48,(byte)0x50,(byte)0xfd,(byte)0xed,(byte)0xb9,(byte)0xda,
                    (byte)0x5e,(byte)0x15,(byte)0x46,(byte)0x57,(byte)0xa7,(byte)0x8d,(byte)0x9d,(byte)0x84,
                    (byte)0x90,(byte)0xd8,(byte)0xab,(byte)0x00,(byte)0x8c,(byte)0xbc,(byte)0xd3,(byte)0x0a,
                    (byte)0xf7,(byte)0xe4,(byte)0x58,(byte)0x05,(byte)0xb8,(byte)0xb3,(byte)0x45,(byte)0x06,
                    (byte)0xd0,(byte)0x2c,(byte)0x1e,(byte)0x8f,(byte)0xca,(byte)0x3f,(byte)0x0f,(byte)0x02,
                    (byte)0xc1,(byte)0xaf,(byte)0xbd,(byte)0x03,(byte)0x01,(byte)0x13,(byte)0x8a,(byte)0x6b,
                    (byte)0x3a,(byte)0x91,(byte)0x11,(byte)0x41,(byte)0x4f,(byte)0x67,(byte)0xdc,(byte)0xea,
                    (byte)0x97,(byte)0xf2,(byte)0xcf,(byte)0xce,(byte)0xf0,(byte)0xb4,(byte)0xe6,(byte)0x73,
                    (byte)0x96,(byte)0xac,(byte)0x74,(byte)0x22,(byte)0xe7,(byte)0xad,(byte)0x35,(byte)0x85,
                    (byte)0xe2,(byte)0xf9,(byte)0x37,(byte)0xe8,(byte)0x1c,(byte)0x75,(byte)0xdf,(byte)0x6e,
                    (byte)0x47,(byte)0xf1,(byte)0x1a,(byte)0x71,(byte)0x1d,(byte)0x29,(byte)0xc5,(byte)0x89,
                    (byte)0x6f,(byte)0xb7,(byte)0x62,(byte)0x0e,(byte)0xaa,(byte)0x18,(byte)0xbe,(byte)0x1b,
                    (byte)0xfc,(byte)0x56,(byte)0x3e,(byte)0x4b,(byte)0xc6,(byte)0xd2,(byte)0x79,(byte)0x20,
                    (byte)0x9a,(byte)0xdb,(byte)0xc0,(byte)0xfe,(byte)0x78,(byte)0xcd,(byte)0x5a,(byte)0xf4,
                    (byte)0x1f,(byte)0xdd,(byte)0xa8,(byte)0x33,(byte)0x88,(byte)0x07,(byte)0xc7,(byte)0x31,
                    (byte)0xb1,(byte)0x12,(byte)0x10,(byte)0x59,(byte)0x27,(byte)0x80,(byte)0xec,(byte)0x5f,
                    (byte)0x60,(byte)0x51,(byte)0x7f,(byte)0xa9,(byte)0x19,(byte)0xb5,(byte)0x4a,(byte)0x0d,
                    (byte)0x2d,(byte)0xe5,(byte)0x7a,(byte)0x9f,(byte)0x93,(byte)0xc9,(byte)0x9c,(byte)0xef,
                    (byte)0xa0,(byte)0xe0,(byte)0x3b,(byte)0x4d,(byte)0xae,(byte)0x2a,(byte)0xf5,(byte)0xb0,
                    (byte)0xc8,(byte)0xeb,(byte)0xbb,(byte)0x3c,(byte)0x83,(byte)0x53,(byte)0x99,(byte)0x61,
                    (byte)0x17,(byte)0x2b,(byte)0x04,(byte)0x7e,(byte)0xba,(byte)0x77,(byte)0xd6,(byte)0x26,
                    (byte)0xe1,(byte)0x69,(byte)0x14,(byte)0x63,(byte)0x55,(byte)0x21,(byte)0x0c,(byte)0x7d
            };

            final int[] K = new int[] {
                    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
                    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
                    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
                    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
                    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
                    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
                    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
                    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
            };

            byte[] RCON = new byte[] { (byte)0x01,(byte)0x02,(byte)0x04,(byte)0x08,(byte)0x10,(byte)0x20,(byte)0x40,(byte)0x80,(byte)0x1b,(byte)0x36 };

            int[] bf_crypt_ciphertext = { 0x4f727068, 0x65616e42, 0x65686f6c, 0x64657253, 0x63727944, 0x6f756274 };

            int[] P_orig = { 0x243f6a88, 0x85a308d3, 0x13198a2e, 0x03707344,
                    0xa4093822, 0x299f31d0, 0x082efa98, 0xec4e6c89, 0x452821e6, 0x38d01377,
                    0xbe5466cf, 0x34e90c6c, 0xc0ac29b7, 0xc97c50dd, 0x3f84d5b5, 0xb5470917,
                    0x9216d5d9, 0x8979fb1b };

            int[] S_orig = { 0xd1310ba6, 0x98dfb5ac, 0x2ffd72db, 0xd01adfb7,
                    0xb8e1afed, 0x6a267e96, 0xba7c9045, 0xf12c7f99, 0x24a19947, 0xb3916cf7,
                    0x0801f2e2, 0x858efc16, 0x636920d8, 0x71574e69, 0xa458fea3, 0xf4933d7e,
                    0x0d95748f, 0x728eb658, 0x718bcd58, 0x82154aee, 0x7b54a41d, 0xc25a59b5,
                    0x9c30d539, 0x2af26013, 0xc5d1b023, 0x286085f0, 0xca417918, 0xb8db38ef,
                    0x8e79dcb0, 0x603a180e, 0x6c9e0e8b, 0xb01e8a3e, 0xd71577c1, 0xbd314b27,
                    0x78af2fda, 0x55605c60, 0xe65525f3, 0xaa55ab94, 0x57489862, 0x63e81440,
                    0x55ca396a, 0x2aab10b6, 0xb4cc5c34, 0x1141e8ce, 0xa15486af, 0x7c72e993,
                    0xb3ee1411, 0x636fbc2a, 0x2ba9c55d, 0x741831f6, 0xce5c3e16, 0x9b87931e,
                    0xafd6ba33, 0x6c24cf5c, 0x7a325381, 0x28958677, 0x3b8f4898, 0x6b4bb9af,
                    0xc4bfe81b, 0x66282193, 0x61d809cc, 0xfb21a991, 0x487cac60, 0x5dec8032,
                    0xef845d5d, 0xe98575b1, 0xdc262302, 0xeb651b88, 0x23893e81, 0xd396acc5,
                    0x0f6d6ff3, 0x83f44239, 0x2e0b4482, 0xa4842004, 0x69c8f04a, 0x9e1f9b5e,
                    0x21c66842, 0xf6e96c9a, 0x670c9c61, 0xabd388f0, 0x6a51a0d2, 0xd8542f68,
                    0x960fa728, 0xab5133a3, 0x6eef0b6c, 0x137a3be4, 0xba3bf050, 0x7efb2a98,
                    0xa1f1651d, 0x39af0176, 0x66ca593e, 0x82430e88, 0x8cee8619, 0x456f9fb4,
                    0x7d84a5c3, 0x3b8b5ebe, 0xe06f75d8, 0x85c12073, 0x401a449f, 0x56c16aa6,
                    0x4ed3aa62, 0x363f7706, 0x1bfedf72, 0x429b023d, 0x37d0d724, 0xd00a1248,
                    0xdb0fead3, 0x49f1c09b, 0x075372c9, 0x80991b7b, 0x25d479d8, 0xf6e8def7,
                    0xe3fe501a, 0xb6794c3b, 0x976ce0bd, 0x04c006ba, 0xc1a94fb6, 0x409f60c4,
                    0x5e5c9ec2, 0x196a2463, 0x68fb6faf, 0x3e6c53b5, 0x1339b2eb, 0x3b52ec6f,
                    0x6dfc511f, 0x9b30952c, 0xcc814544, 0xaf5ebd09, 0xbee3d004, 0xde334afd,
                    0x660f2807, 0x192e4bb3, 0xc0cba857, 0x45c8740f, 0xd20b5f39, 0xb9d3fbdb,
                    0x5579c0bd, 0x1a60320a, 0xd6a100c6, 0x402c7279, 0x679f25fe, 0xfb1fa3cc,
                    0x8ea5e9f8, 0xdb3222f8, 0x3c7516df, 0xfd616b15, 0x2f501ec8, 0xad0552ab,
                    0x323db5fa, 0xfd238760, 0x53317b48, 0x3e00df82, 0x9e5c57bb, 0xca6f8ca0,
                    0x1a87562e, 0xdf1769db, 0xd542a8f6, 0x287effc3, 0xac6732c6, 0x8c4f5573,
                    0x695b27b0, 0xbbca58c8, 0xe1ffa35d, 0xb8f011a0, 0x10fa3d98, 0xfd2183b8,
                    0x4afcb56c, 0x2dd1d35b, 0x9a53e479, 0xb6f84565, 0xd28e49bc, 0x4bfb9790,
                    0xe1ddf2da, 0xa4cb7e33, 0x62fb1341, 0xcee4c6e8, 0xef20cada, 0x36774c01,
                    0xd07e9efe, 0x2bf11fb4, 0x95dbda4d, 0xae909198, 0xeaad8e71, 0x6b93d5a0,
                    0xd08ed1d0, 0xafc725e0, 0x8e3c5b2f, 0x8e7594b7, 0x8ff6e2fb, 0xf2122b64,
                    0x8888b812, 0x900df01c, 0x4fad5ea0, 0x688fc31c, 0xd1cff191, 0xb3a8c1ad,
                    0x2f2f2218, 0xbe0e1777, 0xea752dfe, 0x8b021fa1, 0xe5a0cc0f, 0xb56f74e8,
                    0x18acf3d6, 0xce89e299, 0xb4a84fe0, 0xfd13e0b7, 0x7cc43b81, 0xd2ada8d9,
                    0x165fa266, 0x80957705, 0x93cc7314, 0x211a1477, 0xe6ad2065, 0x77b5fa86,
                    0xc75442f5, 0xfb9d35cf, 0xebcdaf0c, 0x7b3e89a0, 0xd6411bd3, 0xae1e7e49,
                    0x00250e2d, 0x2071b35e, 0x226800bb, 0x57b8e0af, 0x2464369b, 0xf009b91e,
                    0x5563911d, 0x59dfa6aa, 0x78c14389, 0xd95a537f, 0x207d5ba2, 0x02e5b9c5,
                    0x83260376, 0x6295cfa9, 0x11c81968, 0x4e734a41, 0xb3472dca, 0x7b14a94a,
                    0x1b510052, 0x9a532915, 0xd60f573f, 0xbc9bc6e4, 0x2b60a476, 0x81e67400,
                    0x08ba6fb5, 0x571be91f, 0xf296ec6b, 0x2a0dd915, 0xb6636521, 0xe7b9f9b6,
                    0xff34052e, 0xc5855664, 0x53b02d5d, 0xa99f8fa1, 0x08ba4799, 0x6e85076a,
                    0x4b7a70e9, 0xb5b32944, 0xdb75092e, 0xc4192623, 0xad6ea6b0, 0x49a7df7d,
                    0x9cee60b8, 0x8fedb266, 0xecaa8c71, 0x699a17ff, 0x5664526c, 0xc2b19ee1,
                    0x193602a5, 0x75094c29, 0xa0591340, 0xe4183a3e, 0x3f54989a, 0x5b429d65,
                    0x6b8fe4d6, 0x99f73fd6, 0xa1d29c07, 0xefe830f5, 0x4d2d38e6, 0xf0255dc1,
                    0x4cdd2086, 0x8470eb26, 0x6382e9c6, 0x021ecc5e, 0x09686b3f, 0x3ebaefc9,
                    0x3c971814, 0x6b6a70a1, 0x687f3584, 0x52a0e286, 0xb79c5305, 0xaa500737,
                    0x3e07841c, 0x7fdeae5c, 0x8e7d44ec, 0x5716f2b8, 0xb03ada37, 0xf0500c0d,
                    0xf01c1f04, 0x0200b3ff, 0xae0cf51a, 0x3cb574b2, 0x25837a58, 0xdc0921bd,
                    0xd19113f9, 0x7ca92ff6, 0x94324773, 0x22f54701, 0x3ae5e581, 0x37c2dadc,
                    0xc8b57634, 0x9af3dda7, 0xa9446146, 0x0fd0030e, 0xecc8c73e, 0xa4751e41,
                    0xe238cd99, 0x3bea0e2f, 0x3280bba1, 0x183eb331, 0x4e548b38, 0x4f6db908,
                    0x6f420d03, 0xf60a04bf, 0x2cb81290, 0x24977c79, 0x5679b072, 0xbcaf89af,
                    0xde9a771f, 0xd9930810, 0xb38bae12, 0xdccf3f2e, 0x5512721f, 0x2e6b7124,
                    0x501adde6, 0x9f84cd87, 0x7a584718, 0x7408da17, 0xbc9f9abc, 0xe94b7d8c,
                    0xec7aec3a, 0xdb851dfa, 0x63094366, 0xc464c3d2, 0xef1c1847, 0x3215d908,
                    0xdd433b37, 0x24c2ba16, 0x12a14d43, 0x2a65c451, 0x50940002, 0x133ae4dd,
                    0x71dff89e, 0x10314e55, 0x81ac77d6, 0x5f11199b, 0x043556f1, 0xd7a3c76b,
                    0x3c11183b, 0x5924a509, 0xf28fe6ed, 0x97f1fbfa, 0x9ebabf2c, 0x1e153c6e,
                    0x86e34570, 0xeae96fb1, 0x860e5e0a, 0x5a3e2ab3, 0x771fe71c, 0x4e3d06fa,
                    0x2965dcb9, 0x99e71d0f, 0x803e89d6, 0x5266c825, 0x2e4cc978, 0x9c10b36a,
                    0xc6150eba, 0x94e2ea78, 0xa5fc3c53, 0x1e0a2df4, 0xf2f74ea7, 0x361d2b3d,
                    0x1939260f, 0x19c27960, 0x5223a708, 0xf71312b6, 0xebadfe6e, 0xeac31f66,
                    0xe3bc4595, 0xa67bc883, 0xb17f37d1, 0x018cff28, 0xc332ddef, 0xbe6c5aa5,
                    0x65582185, 0x68ab9802, 0xeecea50f, 0xdb2f953b, 0x2aef7dad, 0x5b6e2f84,
                    0x1521b628, 0x29076170, 0xecdd4775, 0x619f1510, 0x13cca830, 0xeb61bd96,
                    0x0334fe1e, 0xaa0363cf, 0xb5735c90, 0x4c70a239, 0xd59e9e0b, 0xcbaade14,
                    0xeecc86bc, 0x60622ca7, 0x9cab5cab, 0xb2f3846e, 0x648b1eaf, 0x19bdf0ca,
                    0xa02369b9, 0x655abb50, 0x40685a32, 0x3c2ab4b3, 0x319ee9d5, 0xc021b8f7,
                    0x9b540b19, 0x875fa099, 0x95f7997e, 0x623d7da8, 0xf837889a, 0x97e32d77,
                    0x11ed935f, 0x16681281, 0x0e358829, 0xc7e61fd6, 0x96dedfa1, 0x7858ba99,
                    0x57f584a5, 0x1b227263, 0x9b83c3ff, 0x1ac24696, 0xcdb30aeb, 0x532e3054,
                    0x8fd948e4, 0x6dbc3128, 0x58ebf2ef, 0x34c6ffea, 0xfe28ed61, 0xee7c3c73,
                    0x5d4a14d9, 0xe864b7e3, 0x42105d14, 0x203e13e0, 0x45eee2b6, 0xa3aaabea,
                    0xdb6c4f15, 0xfacb4fd0, 0xc742f442, 0xef6abbb5, 0x654f3b1d, 0x41cd2105,
                    0xd81e799e, 0x86854dc7, 0xe44b476a, 0x3d816250, 0xcf62a1f2, 0x5b8d2646,
                    0xfc8883a0, 0xc1c7b6a3, 0x7f1524c3, 0x69cb7492, 0x47848a0b, 0x5692b285,
                    0x095bbf00, 0xad19489d, 0x1462b174, 0x23820e00, 0x58428d2a, 0x0c55f5ea,
                    0x1dadf43e, 0x233f7061, 0x3372f092, 0x8d937e41, 0xd65fecf1, 0x6c223bdb,
                    0x7cde3759, 0xcbee7460, 0x4085f2a7, 0xce77326e, 0xa6078084, 0x19f8509e,
                    0xe8efd855, 0x61d99735, 0xa969a7aa, 0xc50c06c2, 0x5a04abfc, 0x800bcadc,
                    0x9e447a2e, 0xc3453484, 0xfdd56705, 0x0e1e9ec9, 0xdb73dbd3, 0x105588cd,
                    0x675fda79, 0xe3674340, 0xc5c43465, 0x713e38d8, 0x3d28f89e, 0xf16dff20,
                    0x153e21e7, 0x8fb03d4a, 0xe6e39f2b, 0xdb83adf7, 0xe93d5a68, 0x948140f7,
                    0xf64c261c, 0x94692934, 0x411520f7, 0x7602d4f7, 0xbcf46b2e, 0xd4a20068,
                    0xd4082471, 0x3320f46a, 0x43b7d4b7, 0x500061af, 0x1e39f62e, 0x97244546,
                    0x14214f74, 0xbf8b8840, 0x4d95fc1d, 0x96b591af, 0x70f4ddd3, 0x66a02f45,
                    0xbfbc09ec, 0x03bd9785, 0x7fac6dd0, 0x31cb8504, 0x96eb27b3, 0x55fd3941,
                    0xda2547e6, 0xabca0a9a, 0x28507825, 0x530429f4, 0x0a2c86da, 0xe9b66dfb,
                    0x68dc1462, 0xd7486900, 0x680ec0a4, 0x27a18dee, 0x4f3ffea2, 0xe887ad8c,
                    0xb58ce006, 0x7af4d6b6, 0xaace1e7c, 0xd3375fec, 0xce78a399, 0x406b2a42,
                    0x20fe9e35, 0xd9f385b9, 0xee39d7ab, 0x3b124e8b, 0x1dc9faf7, 0x4b6d1856,
                    0x26a36631, 0xeae397b2, 0x3a6efa74, 0xdd5b4332, 0x6841e7f7, 0xca7820fb,
                    0xfb0af54e, 0xd8feb397, 0x454056ac, 0xba489527, 0x55533a3a, 0x20838d87,
                    0xfe6ba9b7, 0xd096954b, 0x55a867bc, 0xa1159a58, 0xcca92963, 0x99e1db33,
                    0xa62a4a56, 0x3f3125f9, 0x5ef47e1c, 0x9029317c, 0xfdf8e802, 0x04272f70,
                    0x80bb155c, 0x05282ce3, 0x95c11548, 0xe4c66d22, 0x48c1133f, 0xc70f86dc,
                    0x07f9c9ee, 0x41041f0f, 0x404779a4, 0x5d886e17, 0x325f51eb, 0xd59bc0d1,
                    0xf2bcc18f, 0x41113564, 0x257b7834, 0x602a9c60, 0xdff8e8a3, 0x1f636c1b,
                    0x0e12b4c2, 0x02e1329e, 0xaf664fd1, 0xcad18115, 0x6b2395e0, 0x333e92e1,
                    0x3b240b62, 0xeebeb922, 0x85b2a20e, 0xe6ba0d99, 0xde720c8c, 0x2da2f728,
                    0xd0127845, 0x95b794fd, 0x647d0862, 0xe7ccf5f0, 0x5449a36f, 0x877d48fa,
                    0xc39dfd27, 0xf33e8d1e, 0x0a476341, 0x992eff74, 0x3a6f6eab, 0xf4f8fd37,
                    0xa812dc60, 0xa1ebddf8, 0x991be14c, 0xdb6e6b0d, 0xc67b5510, 0x6d672c37,
                    0x2765d43b, 0xdcd0e804, 0xf1290dc7, 0xcc00ffa3, 0xb5390f92, 0x690fed0b,
                    0x667b9ffb, 0xcedb7d9c, 0xa091cf0b, 0xd9155ea3, 0xbb132f88, 0x515bad24,
                    0x7b9479bf, 0x763bd6eb, 0x37392eb3, 0xcc115979, 0x8026e297, 0xf42e312d,
                    0x6842ada7, 0xc66a2b3b, 0x12754ccc, 0x782ef11c, 0x6a124237, 0xb79251e7,
                    0x06a1bbe6, 0x4bfb6350, 0x1a6b1018, 0x11caedfa, 0x3d25bdd8, 0xe2e1c3c9,
                    0x44421659, 0x0a121386, 0xd90cec6e, 0xd5abea2a, 0x64af674e, 0xda86a85f,
                    0xbebfe988, 0x64e4c3fe, 0x9dbc8057, 0xf0f7c086, 0x60787bf8, 0x6003604d,
                    0xd1fd8346, 0xf6381fb0, 0x7745ae04, 0xd736fccc, 0x83426b33, 0xf01eab71,
                    0xb0804187, 0x3c005e5f, 0x77a057be, 0xbde8ae24, 0x55464299, 0xbf582e61,
                    0x4e58f48f, 0xf2ddfda2, 0xf474ef38, 0x8789bdc2, 0x5366f9c3, 0xc8b38e74,
                    0xb475f255, 0x46fcd9b9, 0x7aeb2661, 0x8b1ddf84, 0x846a0e79, 0x915f95e2,
                    0x466e598e, 0x20b45770, 0x8cd55591, 0xc902de4c, 0xb90bace1, 0xbb8205d0,
                    0x11a86248, 0x7574a99e, 0xb77f19b6, 0xe0a9dc09, 0x662d09a1, 0xc4324633,
                    0xe85a1f02, 0x09f0be8c, 0x4a99a025, 0x1d6efe10, 0x1ab93d1d, 0x0ba5a4df,
                    0xa186f20f, 0x2868f169, 0xdcb7da83, 0x573906fe, 0xa1e2ce9b, 0x4fcd7f52,
                    0x50115e01, 0xa70683fa, 0xa002b5c4, 0x0de6d027, 0x9af88c27, 0x773f8641,
                    0xc3604c06, 0x61a806b5, 0xf0177a28, 0xc0f586e0, 0x006058aa, 0x30dc7d62,
                    0x11e69ed7, 0x2338ea63, 0x53c2dd94, 0xc2c21634, 0xbbcbee56, 0x90bcb6de,
                    0xebfc7da1, 0xce591d76, 0x6f05e409, 0x4b7c0188, 0x39720a3d, 0x7c927c24,
                    0x86e3725f, 0x724d9db9, 0x1ac15bb4, 0xd39eb8fc, 0xed545578, 0x08fca5b5,
                    0xd83d7cd3, 0x4dad0fc4, 0x1e50ef5e, 0xb161e6f8, 0xa28514d9, 0x6c51133c,
                    0x6fd5c7e7, 0x56e14ec4, 0x362abfce, 0xddc6c837, 0xd79a3234, 0x92638212,
                    0x670efa8e, 0x406000e0, 0x3a39ce37, 0xd3faf5cf, 0xabc27737, 0x5ac52d1b,
                    0x5cb0679e, 0x4fa33742, 0xd3822740, 0x99bc9bbe, 0xd5118e9d, 0xbf0f7315,
                    0xd62d1c7e, 0xc700c47b, 0xb78c1b6b, 0x21a19045, 0xb26eb1be, 0x6a366eb4,
                    0x5748ab2f, 0xbc946e79, 0xc6a376d2, 0x6549c2c8, 0x530ff8ee, 0x468dde7d,
                    0xd5730a1d, 0x4cd04dc6, 0x2939bbdb, 0xa9ba4650, 0xac9526e8, 0xbe5ee304,
                    0xa1fad5f0, 0x6a2d519a, 0x63ef8ce2, 0x9a86ee22, 0xc089c2b8, 0x43242ef6,
                    0xa51e03aa, 0x9cf2d0a4, 0x83c061ba, 0x9be96a4d, 0x8fe51550, 0xba645bd6,
                    0x2826a2f9, 0xa73a3ae1, 0x4ba99586, 0xef5562e9, 0xc72fefd3, 0xf752f7da,
                    0x3f046f69, 0x77fa0a59, 0x80e4a915, 0x87b08601, 0x9b09e6ad, 0x3b3ee593,
                    0xe990fd5a, 0x9e34d797, 0x2cf0b7d9, 0x022b8b51, 0x96d5ac3a, 0x017da67d,
                    0xd1cf3ed6, 0x7c7d2d28, 0x1f9f25cf, 0xadf2b89b, 0x5ad6b472, 0x5a88f54c,
                    0xe029ac71, 0xe019a5e6, 0x47b0acfd, 0xed93fa9b, 0xe8d3c48d, 0x283b57cc,
                    0xf8d56629, 0x79132e28, 0x785f0191, 0xed756055, 0xf7960e44, 0xe3d35e8c,
                    0x15056dd4, 0x88f46dba, 0x03a16125, 0x0564f0bd, 0xc3eb9e15, 0x3c9057a2,
                    0x97271aec, 0xa93a072a, 0x1b3f6d9b, 0x1e6321f5, 0xf59c66fb, 0x26dcf319,
                    0x7533d928, 0xb155fdf5, 0x03563482, 0x8aba3cbb, 0x28517711, 0xc20ad9f8,
                    0xabcc5167, 0xccad925f, 0x4de81751, 0x3830dc8e, 0x379d5862, 0x9320f991,
                    0xea7a90c2, 0xfb3e7bce, 0x5121ce64, 0x774fbe32, 0xa8b6e37e, 0xc3293d46,
                    0x48de5369, 0x6413e680, 0xa2ae0810, 0xdd6db224, 0x69852dfd, 0x09072166,
                    0xb39a460a, 0x6445c0dd, 0x586cdecf, 0x1c20c8ae, 0x5bbef7dd, 0x1b588d40,
                    0xccd2017f, 0x6bb4e3bb, 0xdda26a7e, 0x3a59ff45, 0x3e350a44, 0xbcb4cdd5,
                    0x72eacea8, 0xfa6484bb, 0x8d6612ae, 0xbf3c6f47, 0xd29be463, 0x542f5d9e,
                    0xaec2771b, 0xf64e6370, 0x740e0d8d, 0xe75b1357, 0xf8721671, 0xaf537d5d,
                    0x4040cb08, 0x4eb4e2cc, 0x34d2466a, 0x0115af84, 0xe1b00428, 0x95983a1d,
                    0x06b89fb4, 0xce6ea048, 0x6f3f3b82, 0x3520ab82, 0x011a1d4b, 0x277227f8,
                    0x611560b1, 0xe7933fdc, 0xbb3a792b, 0x344525bd, 0xa08839e1, 0x51ce794b,
                    0x2f32c9b7, 0xa01fbac9, 0xe01cc87e, 0xbcc7d1f6, 0xcf0111c3, 0xa1e8aac7,
                    0x1a908749, 0xd44fbd9a, 0xd0dadecb, 0xd50ada38, 0x0339c32a, 0xc6913667,
                    0x8df9317c, 0xe0b12b4f, 0xf79e59b7, 0x43f5bb3a, 0xf2d519ff, 0x27d9459c,
                    0xbf97222c, 0x15e6fc2a, 0x0f91fc71, 0x9b941525, 0xfae59361, 0xceb69ceb,
                    0xc2a86459, 0x12baa8d1, 0xb6c1075e, 0xe3056a0c, 0x10d25065, 0xcb03a442,
                    0xe0ec6e0e, 0x1698db3b, 0x4c98a0be, 0x3278e964, 0x9f1f9532, 0xe0d392df,
                    0xd3a0342b, 0x8971f21e, 0x1b0a7441, 0x4ba3348c, 0xc5be7120, 0xc37632d8,
                    0xdf359f8d, 0x9b992f2e, 0xe60b6f47, 0x0fe3f11d, 0xe54cda54, 0x1edad891,
                    0xce6279cf, 0xcd3e7e6f, 0x1618b166, 0xfd2c1d05, 0x848fd2c5, 0xf6fb2299,
                    0xf523f357, 0xa6327623, 0x93a83531, 0x56cccd02, 0xacf08162, 0x5a75ebb5,
                    0x6e163697, 0x88d273cc, 0xde966292, 0x81b949d0, 0x4c50901b, 0x71c65614,
                    0xe6c6c7bd, 0x327a140a, 0x45e1d006, 0xc3f27b9a, 0xc9aa53fd, 0x62a80f00,
                    0xbb25bfe2, 0x35bdd2f6, 0x71126905, 0xb2040222, 0xb6cbcf7c, 0xcd769c2b,
                    0x53113ec0, 0x1640e3d3, 0x38abbd60, 0x2547adf0, 0xba38209c, 0xf746ce76,
                    0x77afa1c5, 0x20756060, 0x85cbfe4e, 0x8ae88dd8, 0x7aaaf9b0, 0x4cf9aa7e,
                    0x1948c25c, 0x02fb8a8c, 0x01c36ae4, 0xd6ebe1f9, 0x90d4f869, 0xa65cdea0,
                    0x3f09252d, 0xc208e69f, 0xb74e6132, 0xce77e25b, 0x578fdfe3, 0x3ac372e6 };

            byte[] base64_code = { '+', '/', 'A', 'B', 'C', 'D', 'E', 'F',
                    'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
                    'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
                    'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

            byte[] base64_code2 = { 'A', 'B', 'C', 'D', 'E', 'F',
                    'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
                    'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
                    'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/' };

            byte[] index_64 = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, 1, 54, 55,
                    56, 57, 58, 59, 60, 61, 62, 63, -1, -1, -1, -1, -1, -1, -1, 2, 3, 4, 5, 6, 7,
                    8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
                    -1, -1, -1, -1, -1, -1, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                    41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, -1, -1, -1, -1, -1 };

            byte[] MUL2  = new byte[256];
            byte[] MUL3  = new byte[256];
            byte[] MUL9  = new byte[256];
            byte[] MUL11 = new byte[256];
            byte[] MUL13 = new byte[256];
            byte[] MUL14 = new byte[256];

            for (int i = 0; i < 256; i++) {
                int a, p, bb;

                a = i & 0xFF; p = 0; bb = 0x02;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL2[i] = (byte) (p & 0xFF);

                a = i & 0xFF; p = 0; bb = 0x03;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL3[i] = (byte) (p & 0xFF);

                a = i & 0xFF; p = 0; bb = 0x09;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL9[i] = (byte) (p & 0xFF);

                a = i & 0xFF; p = 0; bb = 0x0B;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL11[i] = (byte) (p & 0xFF);

                a = i & 0xFF; p = 0; bb = 0x0D;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL13[i] = (byte) (p & 0xFF);

                a = i & 0xFF; p = 0; bb = 0x0E;
                for (int bit = 0; bit < 8; bit++) {
                    if ((bb & 1) != 0) p ^= a;
                    boolean hi = (a & 0x80) != 0;
                    a = (a << 1) & 0xFF;
                    if (hi) a ^= 0x1B;
                    bb >>= 1;
                }
                MUL14[i] = (byte) (p & 0xFF);
            }

            ByteBuffer readLenBuf = ByteBuffer.allocate(6);
            ByteBuffer readBuf = ByteBuffer.allocate(4096);
            ByteBuffer writeBuf = ByteBuffer.allocate(4096);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Selector selector = null;
            byte[] wbuffer = new byte[65536];
            byte[] rbuffer = new byte[65536];
            byte[] tbuffer = new byte[65536];
            byte[] cKey = new byte[0];
            byte[] sKey = new byte[0];
            byte[] oKey = new byte[0];
            boolean loggedIn = false;
            long lastKeepAlive = System.currentTimeMillis();
            long prevKeepAlive = 0;
            long value = 0;
            int line = 3;
            int linetemp = 14;
            int postKeepAlive = 0;
            long time;
            long seed = 0x8A5CB7DD1E2F304FL;
            byte[] username = new byte[0];
            byte[] password = new byte[0];
            byte[] hwid = new byte[0];
            byte[] uid = new byte[0];
            byte[] sessionToken = new byte[0];
            long timestamp = 0;
            byte banned = 1;
            int serverInt = 0;
            int secretKey = 0;
            int runtimeConstant = 0;
            int packetLength = 0;
            int byteCounter = 0;
            boolean loaded = false;
            boolean clientInit = false;
            //System.out.println("Start");
            boolean blocked = false;
            int attempt = 0;
            while (line != -1) {
                if (line != 1) {
                    //System.out.println("line: " + line);
                    //System.out.println("linetemp: " + linetemp);
                }
                time = System.currentTimeMillis();
                switch (line) {
                    case 0 -> { //write keep alive
                        lastKeepAlive = time;
                        byte[] keepAlivePacket = new byte[9];
                        keepAlivePacket[0] = 1;
                        //System.out.println("Keep alive: " + prevKeepAlive);
                        for (int i = 0; i < 8; i++) {
                            keepAlivePacket[8 - i] = (byte) ((prevKeepAlive >> (i * 8)) & 0xFF);
                        }
                        wbuffer = keepAlivePacket;
                        line = 2;
                        linetemp = 1;
                    }
                    case 1 -> { //packet io
                        /*if (clientInit && Echo.authManager == null) {
                            System.exit(0);
                        }
                        if (loaded && Echo.authManager != null) {
                            int packet = Echo.authManager.requestPacket;
                            if (packet != -1) {
                                if (loaded && loggedIn && banned != 1 && sessionToken != null && lastKeepAlive != 0) {
                                    if (packet == 1) {
                                        if (!clientInit) {
                                            clientInit = true;
                                            Echo.authManager.requestPacket = -1;
                                            AuthManager authManager = Echo.authManager;
                                            authManager.login(new String(username), new String(uid), new String(sessionToken), !loggedIn);

                                            Echo.friendManager = new FriendManager();
                                            Echo.particleManager = new ParticleManager();
                                            Echo.handlerManager = new HandlerManager();
                                            Echo.chunkManager = new ChunkManager();
                                            Echo.commandManager = new CommandManager();

                                            Echo.handlerManager.initialize();
                                            EchoAudio.preload();

                                            MathProt.updateVerification(serverInt, secretKey, runtimeConstant);
                                            line = 20;
                                        } else {
                                            line = 11;
                                            linetemp = 28;
                                            continue;
                                        }
                                    }
                                } else {
                                    line = 11;
                                    linetemp = 27;
                                    continue;
                                }
                            }
                        }*/
                        /*if (loaded && !clientInit) {
                            clientInit = true;
                            line = 20;
                            continue;
                        }*/
                        try {
                            selector.selectNow();

                            //Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                            //while(keys.hasNext()) {
                            //	SelectionKey sk = keys.next();

                            JNT.pushLocal(8);
                            for (SelectionKey sk : selector.selectedKeys()) {
                                selector.selectedKeys().remove(sk);

                                if (!sk.isValid()) continue;

                                SocketChannel ch = (SocketChannel) sk.channel();

                                if (sk.isConnectable()) {
                                    if (ch.finishConnect()) {
                                        sk.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                    }
                                }

                                if (sk.isReadable()) {
                                    if (packetLength == 0) {
                                        rbuffer = new byte[6];
                                        int readBytes = ch.read(readLenBuf);
                                        if (readBytes == -1) {
                                            ch.close();
                                            continue;
                                        }

                                        readLenBuf.flip();
                                        if (readLenBuf.remaining() < 6) {
                                            //System.out.println("Incomplete header received.");
                                            return;
                                        }

                                        readLenBuf.get(rbuffer);
                                        readLenBuf.clear();
                                        int index = 1;
                                        byte b1 = rbuffer[index++];

                                        packetLength = b1 & 0x7F;
                                        //System.out.println("Initial payload length: " + packetLength);

                                        rbuffer[0] = b1;
                                        if (packetLength == 126) {
                                            if (rbuffer.length < index + 2) {
                                                line = 11;
                                                linetemp = 10;
                                                continue;
                                            }
                                            packetLength = ((rbuffer[index++] & 0xFF) << 8) | (rbuffer[index] & 0xFF);
                                            packetLength -= 2;
                                        } else if (packetLength == 127) {
                                            if (rbuffer.length < index + 4) {
                                                line = 11;
                                                linetemp = 11;
                                                continue;
                                            }
                                            packetLength = ((rbuffer[index++] & 0xFF) << 24)
                                                    | ((rbuffer[index++] & 0xFF) << 16)
                                                    | ((rbuffer[index++] & 0xFF) << 8)
                                                    | (rbuffer[index] & 0xFF);
                                        } else {
                                            packetLength -= 4;
                                        }

                                        //System.out.println("Payload length: " + packetLength);
                                    }

                                    readBuf.clear();
                                    int r = ch.read(readBuf);
                                    if (r == -1) {
                                        ch.close();
                                        continue;
                                    }

                                    if (r == 0) {
                                        continue;
                                    }

                                    readBuf.flip();
                                    byte[] data = new byte[readBuf.remaining()];
                                    readBuf.get(data);
                                    baos.write(data);
                                    if (data.length > 0) {
                                        //JNT.free(data);
                                    }
                                    byteCounter += r;
                                    //System.out.println("Read " + byteCounter + " / " + packetLength);

                                    if (byteCounter >= packetLength) {
                                        //System.out.println("Full packet received.");
                                        byte b1 = rbuffer[0];
                                        boolean masked = (b1 & 0x80) != 0;
                                        int length = b1 & 0x7F;

                                        int index = 0;
                                        if (length == 126) {
                                            index += 2;
                                        } else if (length != 127) {
                                            index += 4;
                                        }
                                        //System.out.println("index: " + index);

                                        byte[] maskingKey = null;
                                        if (masked) {
                                            maskingKey = new byte[4];
                                            for (int i = 0; i < 4; i++) {
                                                maskingKey[i] = rbuffer[index + i];
                                            }
                                            index += 4;
                                        }

                                        //System.out.println("Packetlength: " + packetLength + ", index: " + index);
                                        int pos = 6 - index;
                                        byte[] payloadData = new byte[packetLength + index];
                                        for (int i = 0; i < index; i++) {
                                            payloadData[i] = rbuffer[i + pos];
                                        }
                                        //JNT.free(rbuffer);
                                        byte[] body = baos.toByteArray();
                                        for (int i = 0; i < packetLength; i++) {
                                            payloadData[i + index] = body[i];
                                        }
                                        //JNT.free(body);
                                        ////System.out.println("Payload array: " + Arrays.toString(payloadData));

                                        if (masked) {
                                            for (int i = 0; i < payloadData.length; i++) {
                                                payloadData[i] ^= maskingKey[i % 4];
                                            }
                                        }
                                        if (maskingKey != null && maskingKey.length > 0) {
                                            //JNT.free(data);
                                        }

                                        rbuffer = payloadData;
                                        baos.reset();
                                        byteCounter = 0;
                                        packetLength = 0;
                                        line = 4;
                                        linetemp = 6;
                                    }
                                }

                                if (sk.isWritable()) {
                                    writeBuf.flip();
                                    if (writeBuf.hasRemaining()) {
                                        ch.write(writeBuf);
                                        blocked = true;
                                    }
                                    writeBuf.compact();
                                    if (linetemp == -1) {
                                        line = -1;
                                        callback.apply(false, null);
                                        Class<?> echoClass = null;
                                        try {
                                            echoClass = Class.forName("hack.echo.client.Echo");
                                        } catch (ClassNotFoundException ignore) {}
                                        if (echoClass != null) {
                                            /*Echo.authManager = null;
                                            Echo.draw2D = null;
                                            Echo.draw3d = null;
                                            Echo.chunkManager = null;
                                            Echo.handlerManager = null;
                                            Echo.screenManager = null;
                                            Echo.featureManager.shutdown(null);
                                            Echo.featureManager = null;*/
                                        }
                                        return;
                                    }
                                    if (postKeepAlive != 0) {
                                        line = postKeepAlive;
                                        postKeepAlive = 0;
                                    }
                                }

                                if (time - lastKeepAlive >= 15000 && loggedIn && !blocked) {
                                    line = 0;
                                }
                                //JNT.deleteRef(sk);
                                //JNT.sleep(1);
                                if (clientInit) {
                                    Thread.sleep(100);
                                }
                            }
                            if (time - lastKeepAlive >= 45000 && loggedIn) {
                                if (sessionToken == null) {
                                    line = 11;
                                    linetemp = 20;
                                    // #TODO write to file
                                } else {
                                    line = 12;
                                }

                                if (selector != null && selector.isOpen()) selector.close();
                            }
                            JNT.popLocal();
                            //JNT.deleteRef(keys);
                        } catch (CancelledKeyException | SocketException e) {
                            //e.printStackTrace();
                            if (sessionToken == null) {
                                line = 11;
                                linetemp = 20;
                                // #TODO write to file
                            } else {
                                line = 12;
                            }

                            if (selector != null && selector.isOpen()) selector.close();
                        }
                    }
                    case 2 -> { //encrypt
                        byte[] iv = new byte[16];
                        new SecureRandom().nextBytes(iv);

                        //System.out.println(String.format("Writing to server with id: %d", wbuffer[0]));

                        int padLen = 16 - (wbuffer.length % 16);
                        byte[] padded = new byte[wbuffer.length + padLen];
                        for (int i = 0; i < wbuffer.length; i++) {
                            padded[i] = wbuffer[i];
                        }

                        for (int i = wbuffer.length; i < padded.length; i++) {
                            padded[i] = (byte) padLen;
                        }

                        byte[] out = new byte[padded.length];
                        byte[] previousBlock = new byte[16];
                        for (int i = 0; i < iv.length; i++) {
                            previousBlock[i] = iv[i];
                        }

                        for (int offset = 0; offset < padded.length; offset += 16) {
                            byte[] bxor = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                bxor[i] = (byte) (padded[offset + i] ^ previousBlock[i]);
                            }
                            ////JNT.free(previousBlock);

                            byte[] w = new byte[16 * 11];
                            for (int i = 0; i < 16; i++) {
                                w[i] = cKey[i];
                            }

                            int bytesGenerated = 16;
                            int rconIter = 0;
                            byte[] temp = new byte[4];

                            while (bytesGenerated < w.length) {
                                for (int i = 0; i < 4; i++) {
                                    temp[i] = w[bytesGenerated - 4 + i];
                                }

                                if (bytesGenerated % 16 == 0) {
                                    byte t = temp[0];
                                    temp[0] = temp[1];
                                    temp[1] = temp[2];
                                    temp[2] = temp[3];
                                    temp[3] = t;

                                    for (int i = 0; i < 4; i++) {
                                        temp[i] = SBOX[temp[i] & 0xFF];
                                    }

                                    temp[0] ^= RCON[rconIter++];
                                }

                                for (int i = 0; i < 4; i++) {
                                    w[bytesGenerated] = (byte) (w[bytesGenerated - 16] ^ temp[i]);
                                    bytesGenerated++;
                                }
                            }
                            ////JNT.free(temp);

                            int o = 0;
                            for (int i = 0; i < 16; i++) {
                                bxor[i] ^= w[o + i];
                            }

                            for (int round = 1; round < 10; round++) {
                                for (int i = 0; i < 16; i++) {
                                    bxor[i] = SBOX[bxor[i] & 0xFF];
                                }

                                byte tmp;
                                tmp = bxor[1];
                                bxor[1] = bxor[5];
                                bxor[5] = bxor[9];
                                bxor[9] = bxor[13];
                                bxor[13] = tmp;

                                tmp = bxor[2];
                                byte tmp2 = bxor[6];
                                bxor[2] = bxor[10];
                                bxor[6] = bxor[14];
                                bxor[10] = tmp;
                                bxor[14] = tmp2;

                                tmp = bxor[3];
                                bxor[3] = bxor[15];
                                bxor[15] = bxor[11];
                                bxor[11] = bxor[7];
                                bxor[7] = tmp;

                                for (int c = 0; c < 4; c++) {
                                    int i = c * 4;
                                    int a0 = bxor[i] & 0xFF;
                                    int a1 = bxor[i + 1] & 0xFF;
                                    int a2 = bxor[i + 2] & 0xFF;
                                    int a3 = bxor[i + 3] & 0xFF;

                                    bxor[i] = (byte) ((MUL2[a0] ^ MUL3[a1] ^ a2 ^ a3) & 0xFF);
                                    bxor[i + 1] = (byte) ((a0 ^ MUL2[a1] ^ MUL3[a2] ^ a3) & 0xFF);
                                    bxor[i + 2] = (byte) ((a0 ^ a1 ^ MUL2[a2] ^ MUL3[a3]) & 0xFF);
                                    bxor[i + 3] = (byte) ((MUL3[a0] ^ a1 ^ a2 ^ MUL2[a3]) & 0xFF);
                                }
                                o = round * 16;
                                for (int i = 0; i < 16; i++) {
                                    bxor[i] ^= w[o + i];
                                }
                            }
                            for (int i = 0; i < 16; i++) {
                                bxor[i] = SBOX[bxor[i] & 0xFF];
                            }

                            byte tmp;
                            tmp = bxor[1];
                            bxor[1] = bxor[5];
                            bxor[5] = bxor[9];
                            bxor[9] = bxor[13];
                            bxor[13] = tmp;

                            tmp = bxor[2];
                            byte tmp2 = bxor[6];
                            bxor[2] = bxor[10];
                            bxor[6] = bxor[14];
                            bxor[10] = tmp;
                            bxor[14] = tmp2;

                            tmp = bxor[3];
                            bxor[3] = bxor[15];
                            bxor[15] = bxor[11];
                            bxor[11] = bxor[7];
                            bxor[7] = tmp;

                            o = 10 * 16;
                            for (int i = 0; i < 16; i++) {
                                bxor[i] ^= w[o + i];
                            }

                            ////JNT.free(w);
                            for (int i = 0; i < 16; i++) {
                                out[offset + i] = bxor[i];
                            }
                            previousBlock = bxor;
                        }
                        ////JNT.free(padded);
                        ////JNT.free(previousBlock);

                        for (int i = 0; i < iv.length; i++) {
                            iv[i] = (byte) (iv[i] ^ 0x24);
                        }

                        int randomPos = new SecureRandom().nextInt(5, out.length) & Integer.MAX_VALUE;
                        //check if someone hooks SecureRandom and goes out of bounds
                        if (randomPos >= out.length) {
                            line = 11;
                            linetemp = 9;
                            continue;
                        }

                        byte[] finalOut = new byte[iv.length + out.length + 4];
                        for (int i = 0; i < 1; i++) {
                            finalOut[i] = out[i];
                        }
                        int random = (randomPos ^ 36) << 1;
                        finalOut[1] = (byte) ((random >> 24) & 0xFF);
                        finalOut[2] = (byte) ((random >> 16) & 0xFF);
                        finalOut[3] = (byte) ((random >> 8) & 0xFF);
                        finalOut[4] = (byte) (random & 0xFF);
                        for (int i = 1; i < randomPos; i++) {
                            finalOut[4 + i] = out[i];
                        }
                        for (int i = 0; i < iv.length; i++) {
                            finalOut[4 + randomPos + i] = iv[i];
                        }
                        for (int i = randomPos; i < out.length; i++) {
                            finalOut[4 + iv.length + i] = out[i];
                        }
                        ////JNT.free(iv);

                        int frameCount;
                        byte[] frame = new byte[6];

                        frame[0] = (byte) 129;

                        if (finalOut.length <= 125) {
                            frame[1] = (byte) finalOut.length;
                            frameCount = 2;
                        } else if (finalOut.length <= 65535) {
                            int len = finalOut.length;
                            frame[1] = 126;
                            frame[2] = (byte) ((len >> 8) & 0xFF);
                            frame[3] = (byte) (len & 0xFF);
                            frameCount = 4;
                        } else {
                            int len = finalOut.length;
                            frame[1] = 127;
                            frame[2] = (byte) ((len >> 24) & 0xFF);
                            frame[3] = (byte) ((len >> 16) & 0xFF);
                            frame[4] = (byte) ((len >> 8) & 0xFF);
                            frame[5] = (byte) (len & 0xFF);
                            frameCount = 6;
                        }

                        int bLength = frameCount + finalOut.length;

                        byte[] reply = new byte[bLength];

                        int bLim = 0;
                        for (int i = 0; i < frameCount; i++) {
                            reply[bLim] = frame[i];
                            bLim++;
                        }
                        ////JNT.free(frame);
                        for (byte rawDatum : finalOut) {
                            reply[bLim] = rawDatum;
                            bLim++;
                        }
                        ////JNT.free(finalOut);

                        ////JNT.free(wbuffer);
                        wbuffer = new byte[0];
                        writeBuf.put(reply);
                        ////JNT.free(reply);
                        line = linetemp;
                    }
                    case 3 -> { //hash password
                        int H = 32;
                        int B = 64;
                        int dkLen = 16;
                        int iterations = 2048;

                        //System.out.println("seed: " + seed);
                        seed ^= 0x63C4E8A23C1D0A7FL;

                        // String EchoUw!*R9C&.c:/"?}pjJCL!jM/QZ]:
                        int a2 = -902233299;
                        int k1 = a2 + -1061017401;
                        int b2 = -202309908;
                        int k2 = b2 - 481008153;
                        byte[] salt = new byte[32];
                        salt[0] = (byte) 178;
                        salt[1] = (byte) 124;
                        salt[2] = (byte) 211;
                        salt[3] = (byte) 244;
                        salt[4] = (byte) 74;
                        salt[5] = (byte) 196;
                        salt[6] = (byte) 151;
                        salt[7] = (byte) 192;
                        salt[8] = (byte) 165;
                        salt[9] = (byte) 68;
                        salt[10] = (byte) 239;
                        salt[11] = (byte) 244;
                        salt[12] = (byte) 248;
                        salt[13] = (byte) 211;
                        salt[14] = (byte) 206;
                        salt[15] = (byte) 201;
                        salt[16] = (byte) 134;
                        salt[17] = (byte) 39;
                        salt[18] = (byte) 203;
                        salt[19] = (byte) 143;
                        salt[20] = (byte) 193;
                        salt[21] = (byte) 197;
                        salt[22] = (byte) 92;
                        salt[23] = (byte) 122;
                        salt[24] = (byte) 129;
                        salt[25] = (byte) 151;
                        salt[26] = (byte) 120;
                        salt[27] = (byte) 170;
                        salt[28] = (byte) 186;
                        salt[29] = (byte) 89;
                        salt[30] = (byte) 113;
                        salt[31] = (byte) 120;
                        int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                        if (n1 < 0) {
                            n1 += 256;
                        }

                        int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                        if (n2 < 0) {
                            n2 += 256;
                        }

                        for (int i = 0; i < salt.length; i++) {
                            int n3 = i % 2;
                            int c = salt[i] & 0xFF;
                            if (n3 == 0) {
                                int out = c ^ n1;
                                salt[i] = (byte) out;
                                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c ^ n2;
                                salt[i] = (byte) out;
                                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }

                        byte[] key = tbuffer;
                        //System.out.println("salt: " + new String(salt));

                        seed ^= 0xA7F1C3D4B5E69870L;
                        //System.out.println("seed2: " + seed);
                        if (cKey.length == 0) {
                            // String 7.,N[Gf_vU5(LXY!BPco@1[_GW?TbAx;#?!<!!^IS3(vG.g,#_aYT_(u8>#==Nq(
                            int a = 567711214;
                            int k3 = a - 1931556918;
                            int b = -1838589840;
                            int k4 = b - -2043509834;
                            key = new byte[64];
                            key[0] = (byte) 117;
                            key[1] = (byte) 229;
                            key[2] = (byte) 189;
                            key[3] = (byte) 234;
                            key[4] = (byte) 154;
                            key[5] = (byte) 218;
                            key[6] = (byte) 3;
                            key[7] = (byte) 51;
                            key[8] = (byte) 108;
                            key[9] = (byte) 23;
                            key[10] = (byte) 254;
                            key[11] = (byte) 132;
                            key[12] = (byte) 121;
                            key[13] = (byte) 75;
                            key[14] = (byte) 35;
                            key[15] = (byte) 241;
                            key[16] = (byte) 163;
                            key[17] = (byte) 176;
                            key[18] = (byte) 178;
                            key[19] = (byte) 26;
                            key[20] = (byte) 235;
                            key[21] = (byte) 108;
                            key[22] = (byte) 113;
                            key[23] = (byte) 64;
                            key[24] = (byte) 65;
                            key[25] = (byte) 176;
                            key[26] = (byte) 184;
                            key[27] = (byte) 234;
                            key[28] = (byte) 147;
                            key[29] = (byte) 58;
                            key[30] = (byte) 221;
                            key[31] = (byte) 194;
                            key[32] = (byte) 24;
                            key[33] = (byte) 79;
                            key[34] = (byte) 223;
                            key[35] = (byte) 132;
                            key[36] = (byte) 184;
                            key[37] = (byte) 40;
                            key[38] = (byte) 82;
                            key[39] = (byte) 91;
                            key[40] = (byte) 116;
                            key[41] = (byte) 245;
                            key[42] = (byte) 128;
                            key[43] = (byte) 180;
                            key[44] = (byte) 10;
                            key[45] = (byte) 206;
                            key[46] = (byte) 135;
                            key[47] = (byte) 255;
                            key[48] = (byte) 168;
                            key[49] = (byte) 26;
                            key[50] = (byte) 21;
                            key[51] = (byte) 228;
                            key[52] = (byte) 108;
                            key[53] = (byte) 109;
                            key[54] = (byte) 223;
                            key[55] = (byte) 173;
                            key[56] = (byte) 55;
                            key[57] = (byte) 11;
                            key[58] = (byte) 11;
                            key[59] = (byte) 150;
                            key[60] = (byte) 29;
                            key[61] = (byte) 78;
                            key[62] = (byte) 56;
                            key[63] = (byte) 218;
                            oKey = new byte[key.length];
                            for (int i = 0; i < key.length; i++) {
                                oKey[i] = key[i];
                            }
                            int n3 = ((k3 ^= k4) & 0xFF) - (int) seed;
                            if (n3 < 0) {
                                n3 += 256;
                            }

                            int n4 = ((k3 & 0xFFFF) >>> 8) - (int) seed;
                            if (n4 < 0) {
                                n4 += 256;
                            }

                            for (int i = 0; i < key.length; i++) {
                                int n5 = i % 2;
                                int c = key[i] & 0xFF;
                                if (n5 == 0) {
                                    int out = c + n3;
                                    key[i] = (byte) out;
                                    n3 = ((((n3 & 0xFF) >>> 3) | ((n3 & 0xFF) << 5)) ^ out) & 0xFF;
                                } else {
                                    int out = c + n4;
                                    key[i] = (byte) out;
                                    n4 = ((((n4 & 0xFF) << 3) | ((n4 & 0xFF) >>> 5)) ^ out) & 0xFF;
                                }
                            }
                        }

                        byte[] K0 = new byte[B];
                        byte[] shaIn = null;
                        byte[] shaOut = new byte[32];
                        int pc = 0;
                        int ret = 0;
                        int i, t;

                        int l = 0;
                        byte[] derived = null;
                        int dest = 0;
                        int block = 0;

                        byte[] si = null;
                        byte[] ipad = null;
                        byte[] opad = null;
                        byte[] U = null;
                        byte[] T = null;
                        int jcnt = 0;

                        boolean done = false;
                        while (!done) {
                            switch (pc) {
                                case 0:
                                    if (key.length > B) {
                                        shaIn = key;
                                        ret = 1;
                                        pc = 9;
                                    } else {
                                        for (i = 0; i < key.length; i++) {
                                            K0[i] = key[i];
                                        }

                                        for (i = key.length; i < B; i++) {
                                            K0[i] = 0;
                                        }

                                        pc = 2;
                                    }
                                    break;
                                case 1:
                                    for (i = 0; i < 32; i++) {
                                        K0[i] = shaOut[i];
                                    }

                                    for (i = 32; i < B; i++) {
                                        K0[i] = 0;
                                    }

                                    pc = 2;
                                    break;
                                case 2:
                                    l = (dkLen + H - 1) / H;
                                    derived = new byte[l * H];
                                    block = 1;
                                    pc = 3;
                                    break;
                                case 3:
                                    if (block > l) {
                                        byte[] dk = new byte[dkLen];
                                        for (i = 0; i < dkLen; i++) {
                                            dk[i] = derived[i];
                                        }

                                        if (cKey.length == 0) {
                                            cKey = dk;
                                            sKey = dk;
                                        } else {
                                            tbuffer = dk;
                                        }
                                        line = linetemp;
                                        done = true;
                                        ////JNT.free(K0);
                                        ////JNT.free(derived);
                                        ////JNT.free(shaIn);
                                        ////JNT.free(shaOut);
                                        ////JNT.free(salt);
                                        continue;
                                    }

                                    if (si != null) {
                                        ////JNT.free(si);
                                    }
                                    si = new byte[salt.length + 4];
                                    for (i = 0; i < salt.length; i++) {
                                        si[i] = salt[i];
                                    }

                                    int idx = salt.length;
                                    si[idx++] = (byte) ((block >>> 24) & 0xFF);
                                    si[idx++] = (byte) ((block >>> 16) & 0xFF);
                                    si[idx++] = (byte) ((block >>> 8) & 0xFF);
                                    si[idx] = (byte) (block & 0xFF);

                                    if (ipad != null) {
                                        ////JNT.free(ipad);
                                    }
                                    ipad = new byte[B + si.length];
                                    for (i = 0; i < B; i++) {
                                        ipad[i] = (byte) (K0[i] ^ 0x36);
                                    }

                                    for (i = 0; i < si.length; i++) {
                                        ipad[B + i] = si[i];
                                    }

                                    shaIn = ipad;
                                    ret = 4;
                                    pc = 9;
                                    break;
                                case 4:
                                    if (opad != null) {
                                        ////JNT.free(opad);
                                    }
                                    opad = new byte[B + shaOut.length];
                                    for (i = 0; i < B; i++) {
                                        opad[i] = (byte) (K0[i] ^ 0x5c);
                                    }

                                    for (i = 0; i < shaOut.length; i++) {
                                        opad[B + i] = shaOut[i];
                                    }

                                    shaIn = opad;
                                    ret = 5;
                                    pc = 9;
                                    break;
                                case 5:
                                    if (U != null) {
                                        ////JNT.free(U);
                                    }
                                    U = new byte[H];
                                    for (i = 0; i < H; i++) {
                                        U[i] = shaOut[i];
                                    }

                                    if (T != null) {
                                        ////JNT.free(T);
                                    }
                                    T = new byte[H];
                                    for (i = 0; i < H; i++) {
                                        T[i] = U[i];
                                    }

                                    jcnt = 1;
                                    pc = 6;
                                    break;
                                case 6:
                                    if (jcnt >= iterations) {
                                        for (i = 0; i < H; i++) {
                                            derived[dest + i] = T[i];
                                        }

                                        dest += H;
                                        block++;
                                        pc = 3;
                                    } else {
                                        if (ipad != null) {
                                            ////JNT.free(ipad);
                                        }
                                        ipad = new byte[B + U.length];
                                        for (i = 0; i < B; i++) {
                                            ipad[i] = (byte) (K0[i] ^ 0x36);
                                        }

                                        for (i = 0; i < U.length; i++) {
                                            ipad[B + i] = U[i];
                                        }

                                        shaIn = ipad;
                                        ret = 7;
                                        pc = 9;
                                    }
                                    break;
                                case 7:
                                    if (opad != null) {
                                        ////JNT.free(opad);
                                    }
                                    opad = new byte[B + shaOut.length];
                                    for (i = 0; i < B; i++) {
                                        opad[i] = (byte) (K0[i] ^ 0x5c);
                                    }

                                    for (i = 0; i < shaOut.length; i++) {
                                        opad[B + i] = shaOut[i];
                                    }

                                    shaIn = opad;
                                    ret = 8;
                                    pc = 9;
                                    break;
                                case 8:
                                    for (i = 0; i < H; i++) {
                                        T[i] = (byte) (T[i] ^ shaOut[i]);
                                    }

                                    if (U != null) {
                                        ////JNT.free(U);
                                    }
                                    U = new byte[H];
                                    for (i = 0; i < H; i++) {
                                        U[i] = shaOut[i];
                                    }

                                    jcnt++;
                                    pc = 6;
                                    break;
                                case 9: {
                                    byte[] msg = (shaIn == null) ? new byte[0] : shaIn;
                                    int ml = msg.length;
                                    long L = ((long) ml) * 8L;
                                    int total = ((ml + 9 + 63) / 64) * 64;
                                    byte[] pad = new byte[total];
                                    for (i = 0; i < ml; i++) {
                                        pad[i] = msg[i];
                                    }
                                    ////JNT.free(msg);

                                    pad[ml] = (byte) 0x80;
                                    for (i = ml + 1; i < total - 8; i++) {
                                        pad[i] = 0;
                                    }

                                    int pidx = total - 8;
                                    pad[pidx++] = (byte) ((L >>> 56) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 48) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 40) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 32) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 24) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 16) & 0xFF);
                                    pad[pidx++] = (byte) ((L >>> 8) & 0xFF);
                                    pad[pidx] = (byte) (L & 0xFF);

                                    int h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
                                    int h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

                                    int[] w = new int[64];
                                    int off = 0;
                                    while (off < pad.length) {
                                        for (t = 0; t < 16; t++) {
                                            int p0 = off + t * 4;
                                            w[t] = ((pad[p0] & 0xFF) << 24) | ((pad[p0 + 1] & 0xFF) << 16) | ((pad[p0 + 2] & 0xFF) << 8) | (pad[p0 + 3] & 0xFF);
                                        }

                                        for (t = 16; t < 64; t++) {
                                            int s0 = ((w[t - 15] >>> 7) | (w[t - 15] << (32 - 7))) ^ ((w[t - 15] >>> 18) | (w[t - 15] << (32 - 18))) ^ (w[t - 15] >>> 3);
                                            int s1 = ((w[t - 2] >>> 17) | (w[t - 2] << (32 - 17))) ^ ((w[t - 2] >>> 19) | (w[t - 2] << (32 - 19))) ^ (w[t - 2] >>> 10);
                                            w[t] = (int) ((w[t - 16] & 0xFFFFFFFFL) + (s0 & 0xFFFFFFFFL) + (w[t - 7] & 0xFFFFFFFFL) + (s1 & 0xFFFFFFFFL));
                                        }

                                        int a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, hh = h7;
                                        for (t = 0; t < 64; t++) {
                                            int S1 = ((e >>> 6) | (e << (32 - 6))) ^ ((e >>> 11) | (e << (32 - 11))) ^ ((e >>> 25) | (e << (32 - 25)));
                                            int ch = (e & f) ^ ((~e) & g);
                                            int tmp1 = hh + S1 + ch + K[t] + w[t];
                                            int S0 = ((a >>> 2) | (a << (32 - 2))) ^ ((a >>> 13) | (a << (32 - 13))) ^ ((a >>> 22) | (a << (32 - 22)));
                                            int maj = (a & b) ^ (a & c) ^ (b & c);
                                            int tmp2 = S0 + maj;

                                            hh = g;
                                            g = f;
                                            f = e;
                                            e = d + tmp1;
                                            d = c;
                                            c = b;
                                            b = a;
                                            a = tmp1 + tmp2;
                                        }

                                        h0 += a;
                                        h1 += b;
                                        h2 += c;
                                        h3 += d;
                                        h4 += e;
                                        h5 += f;
                                        h6 += g;
                                        h7 += hh;

                                        off += 64;
                                    }
                                    ////JNT.free(pad);

                                    int p = 0;
                                    shaOut[p++] = (byte) ((h0 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h0 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h0 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h0 & 0xFF);
                                    shaOut[p++] = (byte) ((h1 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h1 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h1 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h1 & 0xFF);
                                    shaOut[p++] = (byte) ((h2 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h2 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h2 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h2 & 0xFF);
                                    shaOut[p++] = (byte) ((h3 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h3 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h3 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h3 & 0xFF);
                                    shaOut[p++] = (byte) ((h4 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h4 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h4 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h4 & 0xFF);
                                    shaOut[p++] = (byte) ((h5 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h5 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h5 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h5 & 0xFF);
                                    shaOut[p++] = (byte) ((h6 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h6 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h6 >>> 8) & 0xFF);
                                    shaOut[p++] = (byte) (h6 & 0xFF);
                                    shaOut[p++] = (byte) ((h7 >>> 24) & 0xFF);
                                    shaOut[p++] = (byte) ((h7 >>> 16) & 0xFF);
                                    shaOut[p++] = (byte) ((h7 >>> 8) & 0xFF);
                                    shaOut[p] = (byte) (h7 & 0xFF);

                                    pc = ret;
                                    break;
                                }
                            }
                        }
                    }
                    case 4 -> { // decrypt
                        //System.out.println("Payload length: " + rbuffer.length);

                        int index = 0;
                        byte bp = rbuffer[index++];
                        int pos = ((rbuffer[index++] & 0xFF) << 24)
                                | ((rbuffer[index++] & 0xFF) << 16)
                                | ((rbuffer[index++] & 0xFF) << 8)
                                | (rbuffer[index++] & 0xFF);

                        pos = (pos >> 1) ^ 36;
                        /*if (pos < 0 || pos >= rbuffer.length) {
                            line = 11;
                            linetemp = 31;
                            continue;
                        }*/
                        //System.out.println("Position: " + pos);
                        byte[] part = new byte[pos - 1];
                        for (int i = 0; i < part.length; i++) {
                            part[i] = rbuffer[index++];
                        }

                        byte[] iv = new byte[16];
                        for (int i = 0; i < iv.length; i++) {
                            iv[i] = rbuffer[index++];
                        }

                        for (int i = 0; i < iv.length; i++) {
                            iv[i] = (byte) (iv[i] ^ 0x24);
                        }

                        byte[] rest = new byte[rbuffer.length - index];
                        for (int i = 0; i < rest.length; i++) {
                            rest[i] = rbuffer[index++];
                        }

                        byte[] combined = new byte[rbuffer.length - 20];
                        int cidx = 0;
                        combined[cidx++] = bp;
                        for (int i = 0; i < part.length; i++) {
                            combined[cidx++] = part[i];
                        }
                        ////JNT.free(part);
                        for (int i = 0; i < rest.length; i++) {
                            combined[cidx++] = rest[i];
                        }
                        ////JNT.free(rest);

                        byte[] out = new byte[combined.length];
                        for (int offset = 0; offset < combined.length; offset += 16) {
                            byte[] block = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                block[i] = combined[offset + i];
                            }

                            byte[] working = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                working[i] = block[i];
                            }

                            byte[] w = new byte[16 * 11];
                            for (int i = 0; i < 16; i++) {
                                w[i] = sKey[i];
                            }

                            int bytesGenerated = 16;
                            int rconIter = 0;
                            byte[] temp = new byte[4];

                            while (bytesGenerated < w.length) {
                                for (int i = 0; i < 4; i++) {
                                    temp[i] = w[bytesGenerated - 4 + i];
                                }

                                if (bytesGenerated % 16 == 0) {
                                    byte t = temp[0];
                                    temp[0] = temp[1];
                                    temp[1] = temp[2];
                                    temp[2] = temp[3];
                                    temp[3] = t;

                                    for (int i = 0; i < 4; i++) {
                                        temp[i] = SBOX[temp[i] & 0xFF];
                                    }

                                    temp[0] ^= RCON[rconIter++];
                                }

                                for (int i = 0; i < 4; i++) {
                                    w[bytesGenerated] = (byte) (w[bytesGenerated - 16] ^ temp[i]);
                                    bytesGenerated++;
                                }
                            }
                            ////JNT.free(temp);

                            int o = 10 * 16;
                            for (int i = 0; i < 16; i++) {
                                working[i] ^= w[o + i];
                            }

                            for (int round = 9; round > 0; round--) {
                                byte tmp;
                                tmp = working[13];
                                working[13] = working[9];
                                working[9] = working[5];
                                working[5] = working[1];
                                working[1] = tmp;

                                tmp = working[2];
                                byte tmp2 = working[6];
                                working[2] = working[10];
                                working[6] = working[14];
                                working[10] = tmp;
                                working[14] = tmp2;

                                tmp = working[3];
                                working[3] = working[7];
                                working[7] = working[11];
                                working[11] = working[15];
                                working[15] = tmp;

                                for (int i = 0; i < 16; i++) {
                                    working[i] = INVERSE_SBOX[working[i] & 0xFF];
                                }

                                o = round * 16;
                                for (int i = 0; i < 16; i++) {
                                    working[i] ^= w[o + i];
                                }

                                for (int c = 0; c < 4; c++) {
                                    int i = c * 4;
                                    int a0 = working[i] & 0xFF;
                                    int a1 = working[i + 1] & 0xFF;
                                    int a2 = working[i + 2] & 0xFF;
                                    int a3 = working[i + 3] & 0xFF;

                                    working[i] = (byte) ((MUL14[a0] ^ MUL11[a1] ^ MUL13[a2] ^ MUL9[a3]) & 0xFF);
                                    working[i + 1] = (byte) ((MUL9[a0] ^ MUL14[a1] ^ MUL11[a2] ^ MUL13[a3]) & 0xFF);
                                    working[i + 2] = (byte) ((MUL13[a0] ^ MUL9[a1] ^ MUL14[a2] ^ MUL11[a3]) & 0xFF);
                                    working[i + 3] = (byte) ((MUL11[a0] ^ MUL13[a1] ^ MUL9[a2] ^ MUL14[a3]) & 0xFF);
                                }
                            }

                            byte tmp;
                            tmp = working[13];
                            working[13] = working[9];
                            working[9] = working[5];
                            working[5] = working[1];
                            working[1] = tmp;

                            tmp = working[2];
                            byte tmp2 = working[6];
                            working[2] = working[10];
                            working[6] = working[14];
                            working[10] = tmp;
                            working[14] = tmp2;

                            tmp = working[3];
                            working[3] = working[7];
                            working[7] = working[11];
                            working[11] = working[15];
                            working[15] = tmp;

                            for (int i = 0; i < 16; i++) {
                                working[i] = INVERSE_SBOX[working[i] & 0xFF];
                            }

                            o = 0;
                            for (int i = 0; i < 16; i++) {
                                working[i] ^= w[o + i];
                            }

                            ////JNT.free(w);
                            byte[] plainBlock = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                plainBlock[i] = (byte) (working[i] ^ iv[i]);
                            }
                            ////JNT.free(working);

                            for (int i = 0; i < 16; i++) {
                                out[offset + i] = plainBlock[i];
                            }
                            ////JNT.free(plainBlock);
                            ////JNT.free(iv);
                            iv = block;
                        }
                        ////JNT.free(combined);

                        int padLen = out[out.length - 1] & 0xFF;
                        byte[] finalOut = new byte[out.length - padLen];
                        for (int i = 0; i < finalOut.length; i++) {
                            finalOut[i] = out[i];
                        }
                        if (out != null) {
                            ////JNT.free(out);
                        }

                        ////JNT.free(rbuffer);
                        rbuffer = finalOut;
                        ////System.out.println("Decrypted data: " + Arrays.toString(rbuffer));
                        line = linetemp;
                    }
                    case 5 -> {
                        byte[] copy = sKey;
                        sKey = tbuffer;
                        tbuffer = copy;
                        int count = 0;
                        for (int i = 0; i < rbuffer.length; i++) {
                            byte c = rbuffer[i];
                            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') count++;
                        }
                        if (count == 0) {
                            line = 11;
                            linetemp = 14;
                            continue;
                        }

                        byte[] clean = new byte[count];
                        int p = 0;
                        for (int i = 0; i < rbuffer.length; i++) {
                            byte c = rbuffer[i];
                            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') clean[p++] = c;
                        }

                        if ((p % 4) != 0) {
                            line = 11;
                            linetemp = 22;
                            continue;
                        }

                        int pad = 0;
                        if (p > 0 && clean[p - 1] == '=') pad++;
                        if (p > 1 && clean[p - 2] == '=') pad++;

                        int outLen = (p / 4) * 3 - pad;
                        byte[] out = new byte[outLen];
                        int outIndex = 0;

                        boolean flagged = false;
                        for (int i = 0; i < p; i += 4) {
                            byte c0 = clean[i];
                            byte c1 = clean[i + 1];
                            byte c2 = clean[i + 2];
                            byte c3 = clean[i + 3];

                            int v0 = (c0 == '=') ? 0
                                    : (c0 >= 'A' && c0 <= 'Z') ? (c0 - 'A')
                                    : (c0 >= 'a' && c0 <= 'z') ? (c0 - 'a' + 26)
                                    : (c0 >= '0' && c0 <= '9') ? (c0 - '0' + 52)
                                    : (c0 == '+') ? 62
                                    : (c0 == '/') ? 63
                                    : -1;
                            int v1 = (c1 == '=') ? 0
                                    : (c1 >= 'A' && c1 <= 'Z') ? (c1 - 'A')
                                    : (c1 >= 'a' && c1 <= 'z') ? (c1 - 'a' + 26)
                                    : (c1 >= '0' && c1 <= '9') ? (c1 - '0' + 52)
                                    : (c1 == '+') ? 62
                                    : (c1 == '/') ? 63
                                    : -1;
                            int v2 = (c2 == '=') ? 0
                                    : (c2 >= 'A' && c2 <= 'Z') ? (c2 - 'A')
                                    : (c2 >= 'a' && c2 <= 'z') ? (c2 - 'a' + 26)
                                    : (c2 >= '0' && c2 <= '9') ? (c2 - '0' + 52)
                                    : (c2 == '+') ? 62
                                    : (c2 == '/') ? 63
                                    : -1;
                            int v3 = (c3 == '=') ? 0
                                    : (c3 >= 'A' && c3 <= 'Z') ? (c3 - 'A')
                                    : (c3 >= 'a' && c3 <= 'z') ? (c3 - 'a' + 26)
                                    : (c3 >= '0' && c3 <= '9') ? (c3 - '0' + 52)
                                    : (c3 == '+') ? 62
                                    : (c3 == '/') ? 63
                                    : -1;

                            if (v0 < 0 || v1 < 0 || v2 < 0 || v3 < 0) {
                                flagged = true;
                                break;
                            }

                            int triple = (v0 << 18) | (v1 << 12) | (v2 << 6) | v3;

                            if (outIndex < outLen) out[outIndex++] = (byte) ((triple >> 16) & 0xFF);
                            if (outIndex < outLen) out[outIndex++] = (byte) ((triple >> 8) & 0xFF);
                            if (outIndex < outLen) out[outIndex++] = (byte) (triple & 0xFF);
                        }
                        ////JNT.free(clean);
                        if (flagged) {
                            line = 11;
                            linetemp = 21;
                        } else {
                            rbuffer = out;
                            line = 4;
                            linetemp = 7;
                        }
                    }
                    case 6 -> {
                        int index = 0;
                        byte id = rbuffer[index++];
                        //System.out.println("Received packet with ID: " + id);
                        blocked = false;
                        switch (id) {
                            case 0 -> {
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] strBytes = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    strBytes[i] = rbuffer[index++];
                                }
                                //System.out.println("Received message: " + new String(strBytes));
                                sKey = strBytes;

                                seed ^= 0xEB4FEA0255C0D854L;
                                // String um4iE~06^i!?g#3U71IB.f$}wYww)@($:yX70%i+%fXT23 .8cvX/uAm:OWOI:>d
                                int a = -872194305;
                                int k1 = a - -724919744;
                                int b = -29800234;
                                int k2 = b + -1252661466;
                                byte[] string = new byte[64];
                                string[0] = (byte) 129;
                                string[1] = (byte) 241;
                                string[2] = (byte) 223;
                                string[3] = (byte) 224;
                                string[4] = (byte) 12;
                                string[5] = (byte) 91;
                                string[6] = (byte) 92;
                                string[7] = (byte) 97;
                                string[8] = (byte) 227;
                                string[9] = (byte) 229;
                                string[10] = (byte) 200;
                                string[11] = (byte) 50;
                                string[12] = (byte) 123;
                                string[13] = (byte) 116;
                                string[14] = (byte) 215;
                                string[15] = (byte) 204;
                                string[16] = (byte) 152;
                                string[17] = (byte) 168;
                                string[18] = (byte) 139;
                                string[19] = (byte) 191;
                                string[20] = (byte) 63;
                                string[21] = (byte) 203;
                                string[22] = (byte) 40;
                                string[23] = (byte) 118;
                                string[24] = (byte) 210;
                                string[25] = (byte) 124;
                                string[26] = (byte) 180;
                                string[27] = (byte) 7;
                                string[28] = (byte) 38;
                                string[29] = (byte) 180;
                                string[30] = (byte) 224;
                                string[31] = (byte) 195;
                                string[32] = (byte) 11;
                                string[33] = (byte) 98;
                                string[34] = (byte) 68;
                                string[35] = (byte) 150;
                                string[36] = (byte) 235;
                                string[37] = (byte) 31;
                                string[38] = (byte) 34;
                                string[39] = (byte) 223;
                                string[40] = (byte) 37;
                                string[41] = (byte) 234;
                                string[42] = (byte) 125;
                                string[43] = (byte) 86;
                                string[44] = (byte) 206;
                                string[45] = (byte) 119;
                                string[46] = (byte) 141;
                                string[47] = (byte) 63;
                                string[48] = (byte) 173;
                                string[49] = (byte) 197;
                                string[50] = (byte) 252;
                                string[51] = (byte) 14;
                                string[52] = (byte) 8;
                                string[53] = (byte) 159;
                                string[54] = (byte) 138;
                                string[55] = (byte) 79;
                                string[56] = (byte) 2;
                                string[57] = (byte) 51;
                                string[58] = (byte) 106;
                                string[59] = (byte) 227;
                                string[60] = (byte) 185;
                                string[61] = (byte) 16;
                                string[62] = (byte) 105;
                                string[63] = (byte) 15;
                                int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                                if (n1 < 0) {
                                    n1 += 256;
                                }

                                int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                                if (n2 < 0) {
                                    n2 += 256;
                                }

                                for (int i = 0; i < string.length; i++) {
                                    int n3 = i % 2;
                                    int c = string[i] & 0xFF;
                                    if (n3 == 0) {
                                        int out = c ^ n1;
                                        string[i] = (byte) out;
                                        n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                                    } else {
                                        int out = c ^ n2;
                                        string[i] = (byte) out;
                                        n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                                    }
                                }
                                tbuffer = string;
                                ////JNT.free(rbuffer);
                                rbuffer = sKey;
                                line = 3;
                                linetemp = 5;
                            }
                            case 1 -> {
                                long keepAliveValue = 0;
                                for (int i = 0; i < 8; i++) {
                                    keepAliveValue = (keepAliveValue << 8) | (rbuffer[index++] & 0xFF);
                                }
                                long key = 0;
                                for (int i = 0; i < 8; i++) {
                                    key = (key << 8) | (rbuffer[index++] & 0xFF);
                                }
                                //System.out.println("Received keep-alive packet. Key: " + key + ", KeepAliveValue: " + keepAliveValue);
                                key ^= 9012476591726549716L;
                                keepAliveValue ^= key;
                                //System.out.println("Decrypted keep-alive packet. Key: " + key + ", KeepAliveValue: " + keepAliveValue);
                                int h = 0;
                                for (byte b : hwid) {
                                    int ch = b & 0xFF;
                                    h = ((h << 5) - h) + ch;
                                }
                                if (prevKeepAlive == 0) {
                                    if (((h & 0x7fffffff | ((long) h << 32)) ^ key) != keepAliveValue) {
                                        line = 11;
                                        linetemp = 19;
                                        continue;
                                    }
                                    line = 16;
                                    value = key;
                                    prevKeepAlive = keepAliveValue;
                                    continue;
                                }
                                if (keepAliveValue == prevKeepAlive) {
                                    line = 11;
                                    linetemp = 24;
                                    continue;
                                }
                                if (key == value) {
                                    line = 11;
                                    linetemp = 25;
                                    continue;
                                }
                                if (keepAliveValue != (prevKeepAlive ^ value)) {
                                    line = 11;
                                    linetemp = 26;
                                    continue;
                                }
                                value = key;
                                prevKeepAlive = keepAliveValue;
                                line = 1;
                            }
                            case 2 -> {
                                //System.out.println("1");
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] busername = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    busername[i] = rbuffer[index++];
                                }
                                //System.out.println("2");
                                if (busername.length == 0) {
                                    callback.apply(false, null);
                                    return;
                                }
                                //System.out.println("3");
                                for (int i = 0; i < username.length; i++) {
                                    byte oa = busername[i];
                                    byte ob = username[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 0;
                                        break;
                                    }
                                }
                                ////JNT.free(busername);
                                //System.out.println("4");
                                if (line == 11) {
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] bpassword = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    bpassword[i] = rbuffer[index++];
                                }
                                //System.out.println("5");
                                byte[] real_salt;
                                byte[] passwordb, saltb;
                                int rounds, off = 28;

                                int saltLength = bpassword.length;
                                if (saltLength < 28) {
                                    line = 11;
                                    linetemp = 1;
                                    continue;
                                }
                                //System.out.println("6");

                                rounds = 12;
                                real_salt = new byte[22];
                                for (int i = off + 3; i < off + 25; i++) {
                                    real_salt[i - (off + 3)] = bpassword[i];
                                }
                                //System.out.println("7");

                                passwordb = new byte[password.length + 1];
                                for (int i = 0; i < password.length; i++) {
                                    passwordb[i] = password[i];
                                }
                                passwordb[passwordb.length - 1] = '\000';

                                //System.out.println("8");
                                int off4 = 0, slen2 = real_salt.length, olen = 0, bi = 0;
                                byte c1, c2, c3, c4, o;

                                saltb = new byte[16];

                                //System.out.println("9");
                                while (off4 < slen2 - 1 && olen < 16) {
                                    byte c = real_salt[off4++];
                                    c1 = index_64[c];
                                    c = real_salt[off4++];
                                    c2 = index_64[c];
                                    if (c1 == -1 || c2 == -1) {
                                        break;
                                    }
                                    o = (byte) (c1 << 2);
                                    o |= (byte) ((c2 & 0x30) >> 4);
                                    saltb[bi++] = o;
                                    if (++olen >= 16 || off4 >= slen2) {
                                        break;
                                    }
                                    c = real_salt[off4++];
                                    c3 = index_64[c];
                                    if (c3 == -1) {
                                        break;
                                    }
                                    o = (byte) ((c2 & 0x0f) << 4);
                                    o |= (byte) ((c3 & 0x3c) >> 2);
                                    saltb[bi++] = o;
                                    if (++olen >= 16 || off4 >= slen2) {
                                        break;
                                    }
                                    c = real_salt[off4++];
                                    c4 = index_64[c];
                                    o = (byte) ((c3 & 0x03) << 6);
                                    o |= c4;
                                    saltb[bi++] = o;
                                    ++olen;
                                }
                                //System.out.println("10");
                                ////JNT.free(real_salt);

                                //int[] cdata = bf_crypt_ciphertext.clone();
                                int[] cdata = new int[bf_crypt_ciphertext.length];
                                for (int i = 0; i < bf_crypt_ciphertext.length; i++) {
                                    cdata[i] = bf_crypt_ciphertext[i];
                                }
                                int clen = cdata.length;
                                byte[] ret;
                                //System.out.println("11");

                                long lrounds = 1L << rounds;

                                int[] koffp = {0}, doffp = {0};
                                int[] lr = {0, 0};
                                int plen = P_orig.length, slen = S_orig.length;
                                //System.out.println("12");

                                int line2 = 0;
                                int linetemp2 = 0;
                                int word = 0;
                                int[] offp = new int[0];
                                byte[] data = new byte[0];
                                int i2 = 0;
                                int ir = 0;
                                boolean PorS = true;
                                boolean isES = true;
                                int mode = 0;
                                //System.out.println("13");
                                while (line2 != -1) {
                                    switch (line2) {
                                        case 0: {
                                            data = switch (mode) {
                                                case 0, 1 -> passwordb;
                                                case 2 -> saltb;
                                                default -> data;
                                            };
                                            offp = koffp;
                                            linetemp2 = 3;
                                            line2 = 2;
                                            break;
                                        }
                                        case 1: {
                                            offp = doffp;
                                            data = saltb;
                                            if (isES) {
                                                linetemp2 = 5;
                                            } else {
                                                line2 = 8;
                                                break;
                                            }
                                            line2 = 2;
                                            break;
                                        }
                                        case 2: {
                                            int off2 = offp[0];

                                            for (int i = 0; i < 4; i++) {
                                                word = (word << 8) | (data[off2] & 0xff);
                                                off2 = (off2 + 1) % data.length;
                                            }

                                            offp[0] = off2;
                                            line2 = linetemp2;
                                            break;
                                        }
                                        case 3: {
                                            P_orig[i2] = P_orig[i2] ^ word;
                                            word = 0;
                                            if (++i2 < plen) {
                                                line2 = 0;
                                                break;
                                            }
                                            i2 = 0;
                                            line2 = 1;
                                            break;
                                        }
                                        case 4: {
                                            lr[1] ^= word;
                                            word = 0;
                                            line2 = 6;
                                            linetemp2 = 7;
                                            break;
                                        }
                                        case 5: {
                                            lr[0] ^= word;
                                            linetemp2 = 4;
                                            line2 = 2;
                                            break;
                                        }
                                        case 6: {
                                            int i3, n, l = lr[word], r = lr[word + 1];

                                            l ^= P_orig[0];
                                            for (i3 = 0; i3 <= 14; ) {
                                                n = S_orig[(l >> 24) & 0xff];
                                                n += S_orig[0x100 | ((l >> 16) & 0xff)];
                                                n ^= S_orig[0x200 | ((l >> 8) & 0xff)];
                                                n += S_orig[0x300 | (l & 0xff)];
                                                r ^= n ^ P_orig[++i3];

                                                n = S_orig[(r >> 24) & 0xff];
                                                n += S_orig[0x100 | ((r >> 16) & 0xff)];
                                                n ^= S_orig[0x200 | ((r >> 8) & 0xff)];
                                                n += S_orig[0x300 | (r & 0xff)];
                                                l ^= n ^ P_orig[++i3];
                                            }
                                            lr[word] = r ^ P_orig[17];
                                            lr[word + 1] = l;
                                            line2 = linetemp2;
                                            break;
                                        }
                                        case 7: {
                                            if (PorS) {
                                                P_orig[i2] = lr[0];
                                                P_orig[i2 + 1] = lr[1];
                                                if ((i2 += 2) < plen) {
                                                    line2 = 1;
                                                    break;
                                                }
                                                i2 = 0;
                                                line2 = 9;
                                            } else {
                                                S_orig[i2] = lr[0];
                                                S_orig[i2 + 1] = lr[1];
                                                if ((i2 += 2) < slen) {
                                                    line2 = 1;
                                                    break;
                                                }
                                                i2 = 0;
                                                line2 = 10;
                                            }
                                            break;
                                        }
                                        case 8: {
                                            line2 = 6;
                                            linetemp2 = 7;
                                            break;
                                        }
                                        case 9: {
                                            PorS = false;
                                            line2 = 1;
                                            break;
                                        }
                                        case 10: {
                                            koffp = new int[]{0};
                                            lr = new int[]{0, 0};
                                            PorS = true;
                                            if (mode == 0) {
                                                mode = 1;
                                                isES = false;
                                                line2 = 0;
                                                break;
                                            }
                                            if (mode == 1) {
                                                mode = 2;
                                                line2 = 0;
                                                ir++;
                                                break;
                                            }
                                            if (ir >= lrounds) {
                                                ir = 0;
                                                line2 = 11;
                                                lr = cdata;
                                            } else {
                                                mode = 1;
                                                line2 = 0;
                                            }
                                            break;
                                        }
                                        case 11: {
                                            if (ir == (clen >> 1)) {
                                                ir = 0;
                                                i2++;
                                            }
                                            if (i2 == 64) {
                                                line2 = -1;
                                                break;
                                            }
                                            word = ir << 1;
                                            line2 = 6;
                                            linetemp2 = 11;
                                            ir++;
                                            break;
                                        }
                                    }
                                }

                                ret = new byte[clen * 4];
                                for (int i3 = 0, j = 0; i3 < clen; i3++) {
                                    ret[j++] = (byte) ((cdata[i3] >> 24) & 0xff);
                                    ret[j++] = (byte) ((cdata[i3] >> 16) & 0xff);
                                    ret[j++] = (byte) ((cdata[i3] >> 8) & 0xff);
                                    ret[j++] = (byte) (cdata[i3] & 0xff);
                                }

                                seed += 0x5BDBCF72CAFAF8D6L;
                                // String $argon2id$v=19$m=65536,t=3,p=4$
                                int a = -1572619662;
                                int k1 = a - 854843218;
                                int b = -1109387647;
                                int k2 = b + 2128787240;
                                byte[] string = new byte[31];
                                string[0] = (byte) 151;
                                string[1] = (byte) 97;
                                string[2] = (byte) 188;
                                string[3] = (byte) 200;
                                string[4] = (byte) 170;
                                string[5] = (byte) 218;
                                string[6] = (byte) 58;
                                string[7] = (byte) 118;
                                string[8] = (byte) 151;
                                string[9] = (byte) 37;
                                string[10] = (byte) 120;
                                string[11] = (byte) 105;
                                string[12] = (byte) 103;
                                string[13] = (byte) 149;
                                string[14] = (byte) 27;
                                string[15] = (byte) 72;
                                string[16] = (byte) 23;
                                string[17] = (byte) 233;
                                string[18] = (byte) 155;
                                string[19] = (byte) 224;
                                string[20] = (byte) 44;
                                string[21] = (byte) 158;
                                string[22] = (byte) 56;
                                string[23] = (byte) 233;
                                string[24] = (byte) 234;
                                string[25] = (byte) 18;
                                string[26] = (byte) 180;
                                string[27] = (byte) 61;
                                string[28] = (byte) 122;
                                string[29] = (byte) 82;
                                string[30] = (byte) 190;
                                int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                                if (n1 < 0) {
                                    n1 += 256;
                                }

                                int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                                if (n2 < 0) {
                                    n2 += 256;
                                }

                                for (int i = 0; i < string.length; i++) {
                                    int n3 = i % 2;
                                    int c = string[i] & 0xFF;
                                    if (n3 == 0) {
                                        int out = c - n1;
                                        string[i] = (byte) out;
                                        n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                                    } else {
                                        int out = c - n2;
                                        string[i] = (byte) out;
                                        n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                                    }
                                }

                                byte[] temp = new byte[65536];
                                int k;
                                for (k = 0; k < string.length; k++) {
                                    temp[k] = string[k];
                                }
                                ////JNT.free(string);
                                int line3 = 2;
                                int linetemp3 = 0;
                                byte[] d = new byte[0];
                                int len = 0;
                                while (line3 != -1) {
                                    switch (line3) {
                                        case 0: {
                                            d = ret;
                                            len = bf_crypt_ciphertext.length * 4 - 1;
                                            line3 = 1;
                                            linetemp3 = -1;
                                            break;
                                        }
                                        case 1: {
                                            int off5 = 0;
                                            int c5, c6;

                                            while (off5 < len) {
                                                c5 = d[off5++] & 0xff;
                                                temp[k++] = base64_code[(c5 >> 2) & 0x3f];
                                                c5 = (c5 & 0x03) << 4;
                                                if (off5 >= len) {
                                                    temp[k++] = base64_code[c5 & 0x3f];
                                                    break;
                                                }
                                                c6 = d[off5++] & 0xff;
                                                c5 |= (c6 >> 4) & 0x0f;
                                                temp[k++] = base64_code[c5 & 0x3f];
                                                c5 = (c6 & 0x0f) << 2;
                                                if (off5 >= len) {
                                                    temp[k++] = base64_code[c5 & 0x3f];
                                                    break;
                                                }
                                                c6 = d[off5++] & 0xff;
                                                c5 |= (c6 >> 6) & 0x03;
                                                temp[k++] = base64_code[c5 & 0x3f];
                                                temp[k++] = base64_code[c6 & 0x3f];
                                            }
                                            line3 = linetemp3;
                                            break;
                                        }
                                        case 2: {
                                            d = saltb;
                                            len = saltb.length;
                                            line3 = 1;
                                            break;
                                        }
                                    }
                                }
                                ////JNT.free(d);
                                k++;
                                byte[] out = new byte[k];
                                for (int j = 0; j < 62; j++) {
                                    out[j] = temp[j];
                                }
                                out[62] = '$';
                                for (int j = 63; j < k; j++) {
                                    out[j] = temp[j - 1];
                                }
                                ////JNT.free(temp);
                                for (int i = 0; i < bpassword.length; i++) {
                                    byte oa = out[i];
                                    byte ob = bpassword[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 2;
                                        break;
                                    }
                                }
                                ////JNT.free(out);
                                if (line == 11) {
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] bhwid = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    bhwid[i] = rbuffer[index++];
                                }
                                for (int i = 0; i < hwid.length; i++) {
                                    byte oa = bhwid[i];
                                    byte ob = hwid[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 3;
                                        break;
                                    }
                                }
                                ////JNT.free(bhwid);
                                if (line == 11) {
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                uid = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    uid[i] = rbuffer[index++];
                                }
                                timestamp = 0;
                                for (int i = 0; i < 8; i++) {
                                    timestamp = (timestamp << 8) | (rbuffer[index++] & 0xFF);
                                }

                                if (timestamp < time - 20000L || timestamp > time + 20000L) {
                                    line = 11;
                                    linetemp = 4;
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                sessionToken = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    sessionToken[i] = rbuffer[index++];
                                }

                                banned = rbuffer[index++];
                                if (banned != 0) {
                                    line = 11;
                                    linetemp = 5;
                                    continue;
                                }

                                for (int i = 0; i < 4; i++) {
                                    serverInt = (serverInt << 8) | (rbuffer[index++] & 0xFF);
                                }
                                for (int i = 0; i < 4; i++) {
                                    secretKey = (secretKey << 8) | (rbuffer[index++] & 0xFF);
                                }
                                for (int i = 0; i < 4; i++) {
                                    runtimeConstant = (runtimeConstant << 8) | (rbuffer[index++] & 0xFF);
                                }

                                loggedIn = true;

                                byte[] keepAlivePacket = new byte[9];
                                keepAlivePacket[0] = 1;
                                for (int i = 1; i < 9; i++) {
                                    keepAlivePacket[i] = 0;
                                }
                                rbuffer = keepAlivePacket;
                                line = 1;
                            }
                            case 3 -> {
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] bsessionToken = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    bsessionToken[i] = rbuffer[index++];
                                }

                                if (bsessionToken.length == 0) {
                                    callback.apply(false, null);
                                    return;
                                }

                                for (int i = 0; i < sessionToken.length; i++) {
                                    byte oa = bsessionToken[i];
                                    byte ob = sessionToken[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 32;
                                        break;
                                    }
                                }

                                if (line == 11) {
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] bhwid = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    bhwid[i] = rbuffer[index++];
                                }

                                for (int i = 0; i < hwid.length; i++) {
                                    byte oa = bhwid[i];
                                    byte ob = hwid[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 3;
                                        break;
                                    }
                                }

                                if (line == 11) {
                                    continue;
                                }

                                strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] buid = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    buid[i] = rbuffer[index++];
                                }

                                for (int i = 0; i < uid.length; i++) {
                                    byte oa = buid[i];
                                    byte ob = uid[i];
                                    if (oa != ob) {
                                        line = 11;
                                        linetemp = 3;
                                        break;
                                    }
                                }

                                if (line == 11) {
                                    continue;
                                }

                                timestamp = 0;
                                for (int i = 0; i < 8; i++) {
                                    timestamp = (timestamp << 8) | (rbuffer[index++] & 0xFF);
                                }

                                if (timestamp < time - 20000L || timestamp > time + 20000L) {
                                    line = 11;
                                    linetemp = 33;
                                    continue;
                                }

                                banned = rbuffer[index];
                                if (banned != 0) {
                                    line = 11;
                                    linetemp = 5;
                                    continue;
                                }

                                byte[] keepAlivePacket = new byte[9];
                                keepAlivePacket[0] = 1;
                                for (int i = 1; i < 9; i++) {
                                    keepAlivePacket[i] = 0;
                                }

                                rbuffer = keepAlivePacket;
                                cKey = tbuffer;
                                sKey = password;
                                tbuffer = new byte[0];
                                password = new byte[0];
                                line = 1;
                            }
                            case 4 -> {
                                callback.apply(false, null);
                                Class<?> echoClass = null;
                                try {
                                    echoClass = Class.forName("hack.echo.client.Echo");
                                } catch (ClassNotFoundException ignore) {}
                                if (echoClass != null) {
                                    /*Echo.authManager = null;
                                    Echo.draw2D = null;
                                    Echo.draw3d = null;
                                    Echo.chunkManager = null;
                                    Echo.handlerManager = null;
                                    Echo.screenManager = null;
                                    Echo.featureManager.shutdown(null);
                                    Echo.featureManager = null;*/
                                }
                                return;
                            }
                            case 5 -> {
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] key = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    key[i] = rbuffer[index++];
                                }
                                int h = 0;
                                for (byte b : key) {
                                    int ch = b & 0xFF;
                                    h = ((h << 5) - h) + ch;
                                }
                                int dataLen = 0;
                                for (int i = 0; i < 4; i++) {
                                    dataLen = (dataLen << 8) | (rbuffer[index++] & 0xFF);
                                }
                                dataLen ^= h;
                                //System.out.println("Jar packet data length: " + dataLen);
                                //System.out.println("Index: " + index + ", Buffer length: " + rbuffer.length);
                                byte[] payload = new byte[dataLen];
                                for (int i = 0; i < dataLen; i++) {
                                    payload[i] = rbuffer[index++];
                                }
                                ////JNT.free(rbuffer);

                                seed ^= 0x2019DBC37CE0AB59L;
                                tbuffer = key;
                                rbuffer = payload;
                                line = 3;
                                linetemp = 18;
                            }
                            case 9 -> {
                                int dataLen = 0;
                                for (int i = 0; i < 4; i++) {
                                    dataLen = (dataLen << 8) | (rbuffer[index++] & 0xFF);
                                }
                                byte[] payload = new byte[dataLen];
                                for (int i = 0; i < dataLen; i++) {
                                    payload[i] = rbuffer[index++];
                                }
                                ////JNT.free(rbuffer);

                                rbuffer = payload;
                                line = 23;
                            }
                            case 10 -> {
                                cKey = tbuffer;
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] key = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    key[i] = rbuffer[index++];
                                }
                                //System.out.println("Setting new sKey: " + new String(key, StandardCharsets.UTF_8));
                                blocked = true;
                                tbuffer = key;
                                line = 3;
                                linetemp = 22;
                            }
                            case 11 -> {
                                int strLen = rbuffer[index++] << 8 | (rbuffer[index++] & 0xFF);
                                byte[] message = new byte[strLen];
                                for (int i = 0; i < strLen; i++) {
                                    message[i] = rbuffer[index++];
                                }

                                if (clientInit) {
                                    //BroadcastHandler.addMessage(new String(message, StandardCharsets.UTF_8));
                                }
                                line = 1;
                            }
                            default -> {
                                line = 11;
                                linetemp = 6;
                            }
                        }
                    }
                    case 7 -> {
                        //System.out.println("key: " + new String(rbuffer, StandardCharsets.UTF_8));
                        int colonIndex = -1;
                        for (int i = 0; i < rbuffer.length; i++) {
                            if (rbuffer[i] == (byte) ':') { // compare bytes directly
                                colonIndex = i;
                                break;
                            }
                        }

                        //byte[][] parts;
                        byte[] first;
                        byte[] second;
                        if (colonIndex == -1) {
                            line = 11;
                            linetemp = 15;
                            continue;
                        } else {
                            int secondLen = rbuffer.length - colonIndex - 1;

                            first = new byte[colonIndex];
                            for (int i = 0; i < colonIndex; i++) {
                                first[i] = rbuffer[i];
                            }

                            second = new byte[secondLen];
                            for (int i = 0; i < secondLen; i++) {
                                second[i] = rbuffer[colonIndex + 1 + i];
                            }

                            //parts = new byte[][]{first, second};
                        }
                        ////JNT.free(rbuffer);
                        byte[] key = second;
                        seed ^= 0x9A6B7C4D3E2F1A0BL;

                        // String 7.,N[Gf_vU5(LXY!BPco@1[_GW?TbAx;#?!<!!^IS3(vG.g,#_aYT_(u8>#==Nq(
                        int a = 2048315216;
                        int k1 = a - -964789282;
                        int b = 589368737;
                        int k2 = b - -1764635545;
                        byte[] string = new byte[64];
                        string[0] = (byte) 58;
                        string[1] = (byte) 103;
                        string[2] = (byte) 164;
                        string[3] = (byte) 62;
                        string[4] = (byte) 30;
                        string[5] = (byte) 121;
                        string[6] = (byte) 106;
                        string[7] = (byte) 46;
                        string[8] = (byte) 125;
                        string[9] = (byte) 127;
                        string[10] = (byte) 236;
                        string[11] = (byte) 69;
                        string[12] = (byte) 48;
                        string[13] = (byte) 33;
                        string[14] = (byte) 138;
                        string[15] = (byte) 64;
                        string[16] = (byte) 162;
                        string[17] = (byte) 34;
                        string[18] = (byte) 13;
                        string[19] = (byte) 78;
                        string[20] = (byte) 151;
                        string[21] = (byte) 203;
                        string[22] = (byte) 230;
                        string[23] = (byte) 93;
                        string[24] = (byte) 82;
                        string[25] = (byte) 8;
                        string[26] = (byte) 70;
                        string[27] = (byte) 39;
                        string[28] = (byte) 98;
                        string[29] = (byte) 4;
                        string[30] = (byte) 22;
                        string[31] = (byte) 147;
                        string[32] = (byte) 239;
                        string[33] = (byte) 193;
                        string[34] = (byte) 124;
                        string[35] = (byte) 112;
                        string[36] = (byte) 140;
                        string[37] = (byte) 199;
                        string[38] = (byte) 203;
                        string[39] = (byte) 86;
                        string[40] = (byte) 39;
                        string[41] = (byte) 93;
                        string[42] = (byte) 82;
                        string[43] = (byte) 241;
                        string[44] = (byte) 85;
                        string[45] = (byte) 212;
                        string[46] = (byte) 78;
                        string[47] = (byte) 48;
                        string[48] = (byte) 223;
                        string[49] = (byte) 148;
                        string[50] = (byte) 182;
                        string[51] = (byte) 88;
                        string[52] = (byte) 64;
                        string[53] = (byte) 14;
                        string[54] = (byte) 82;
                        string[55] = (byte) 160;
                        string[56] = (byte) 70;
                        string[57] = (byte) 99;
                        string[58] = (byte) 189;
                        string[59] = (byte) 93;
                        string[60] = (byte) 78;
                        string[61] = (byte) 20;
                        string[62] = (byte) 177;
                        string[63] = (byte) 137;
                        int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                        if (n1 < 0) {
                            n1 += 256;
                        }

                        int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                        if (n2 < 0) {
                            n2 += 256;
                        }

                        for (int i = 0; i < string.length; i++) {
                            int n3 = i % 2;
                            int c = string[i] & 0xFF;
                            if (n3 == 0) {
                                int out = c + n1;
                                string[i] = (byte) out;
                                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c + n2;
                                string[i] = (byte) out;
                                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }

                        for (int i = 0; i < string.length; i++) {
                            byte oa = key[i];
                            byte ob = string[i];
                            if (oa != ob) {
                                line = 11;
                                linetemp = 7;
                                break;
                            }
                        }
                        ////JNT.free(key);
                        ////JNT.free(string);
                        if (line == 11) {
                            continue;
                        }

                        long result = 0L;
                        long limit = -0x7fffffffffffffffL;
                        long multmin = limit / 10L;
                        int i = 0;
                        while (i < first.length) {
                            int digit = first[i++] - '0';
                            if (digit < 0 || digit > 9) {
                                line = 11;
                                linetemp = 16;
                                break;
                            }
                            if (result < multmin) {
                                line = 11;
                                linetemp = 17;
                                break;
                            }
                            result *= 10L;
                            if (result < (limit + digit)) {
                                line = 11;
                                linetemp = 18;
                                break;
                            }
                            result -= digit;
                        }
                        if (line == 11) {
                            continue;
                        }

                        if (-result < time - 60000L || -result > time + 60000L) {
                            line = 11;
                            linetemp = 8;
                            continue;
                        }

                        seed ^= 0x5E5E573BB7D48804L;
                        rbuffer = new byte[0];
                        line = 3;
                        linetemp = 10;
                    }
                    case 8 -> { // send login packet
                        cKey = tbuffer;
                        tbuffer = new byte[0];
                        wbuffer = new byte[susername.length() + spassword.length() + shwid.length() + 7];
                        int pos = 0;
                        wbuffer[pos++] = 2;
                        wbuffer[pos++] = (byte) ((susername.length() >> 8) & 0xFF);
                        wbuffer[pos++] = (byte) (susername.length() & 0xFF);
                        int ui = 0;
                        username = new byte[susername.length()];
                        for (byte c : susername.getBytes()) {
                            wbuffer[pos++] = c;
                            username[ui++] = c;
                        }
                        wbuffer[pos++] = (byte) ((spassword.length() >> 8) & 0xFF);
                        wbuffer[pos++] = (byte) (spassword.length() & 0xFF);
                        int pi = 0;
                        password = new byte[spassword.length()];
                        for (byte c : spassword.getBytes()) {
                            wbuffer[pos++] = c;
                            password[pi++] = c;
                        }
                        wbuffer[pos++] = (byte) ((shwid.length() >> 8) & 0xFF);
                        wbuffer[pos++] = (byte) (shwid.length() & 0xFF);
                        int hi = 0;
                        hwid = new byte[shwid.length()];
                        for (byte c : shwid.getBytes()) {
                            wbuffer[pos++] = c;
                            hwid[hi++] = c;
                        }
                        line = 2;
                        linetemp = 1;
                    }
                    case 9 -> { // init
                        SocketChannel channel = SocketChannel.open();
                        channel.configureBlocking(true);
                        channel.connect(new InetSocketAddress("ws.echoclient.net", 80));
                        //channel.connect(new InetSocketAddress("localhost", 6767));

                        InputStream inStream = Channels.newInputStream(channel);
                        OutputStream outStream = Channels.newOutputStream(channel);
                        DataInputStream din = new DataInputStream(inStream);
                        DataOutputStream dout = new DataOutputStream(outStream);

                        byte[] random = new byte[16];
                        new SecureRandom().nextBytes(random);
                        String secWebSocketKey = Base64.getEncoder().encodeToString(random);
                        ////JNT.free(random);

                        // StringBuilder
                        int index = 0;
                        int line2 = 3;
                        byte[] lbuffer = new byte[65536];
                        while (line2 != -1) {
                            switch (line2) {
                                case 1 -> {
                                    lbuffer[index++] = 'H';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = 'w';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = '.';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'h';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'l';
                                    lbuffer[index++] = 'i';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '.';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 5;
                                }
                                case 2 -> {
                                    lbuffer[index++] = 'C';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = 'i';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = 'U';
                                    lbuffer[index++] = 'p';
                                    lbuffer[index++] = 'g';
                                    lbuffer[index++] = 'r';
                                    lbuffer[index++] = 'a';
                                    lbuffer[index++] = 'd';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 7;
                                }
                                case 3 -> {
                                    lbuffer[index++] = 'G';
                                    lbuffer[index++] = 'E';
                                    lbuffer[index++] = 'T';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = '/';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = 'H';
                                    lbuffer[index++] = 'T';
                                    lbuffer[index++] = 'T';
                                    lbuffer[index++] = 'P';
                                    lbuffer[index++] = '/';
                                    lbuffer[index++] = '1';
                                    lbuffer[index++] = '.';
                                    lbuffer[index++] = '1';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 1;
                                }
                                case 4 -> {
                                    lbuffer[index++] = 'S';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = '-';
                                    lbuffer[index++] = 'W';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'b';
                                    lbuffer[index++] = 'S';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'k';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '-';
                                    lbuffer[index++] = 'V';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'r';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = 'i';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = '1';
                                    lbuffer[index++] = '3';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 6;
                                }
                                case 5 -> {
                                    lbuffer[index++] = 'U';
                                    lbuffer[index++] = 'p';
                                    lbuffer[index++] = 'g';
                                    lbuffer[index++] = 'r';
                                    lbuffer[index++] = 'a';
                                    lbuffer[index++] = 'd';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = 'w';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'b';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'k';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 2;
                                }
                                case 6 -> {
                                    lbuffer[index++] = 'U';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'r';
                                    lbuffer[index++] = '-';
                                    lbuffer[index++] = 'A';
                                    lbuffer[index++] = 'g';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    lbuffer[index++] = 'E';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'h';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'C';
                                    lbuffer[index++] = 'l';
                                    lbuffer[index++] = 'i';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'n';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '/';
                                    lbuffer[index++] = '1';
                                    lbuffer[index++] = '.';
                                    lbuffer[index++] = '0';
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 8;
                                }
                                case 7 -> {
                                    lbuffer[index++] = 'S';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = '-';
                                    lbuffer[index++] = 'W';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'b';
                                    lbuffer[index++] = 's';
                                    lbuffer[index++] = 'o';
                                    lbuffer[index++] = 'c';
                                    lbuffer[index++] = 'k';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 't';
                                    lbuffer[index++] = '-';
                                    lbuffer[index++] = 'K';
                                    lbuffer[index++] = 'e';
                                    lbuffer[index++] = 'y';
                                    lbuffer[index++] = ':';
                                    lbuffer[index++] = ' ';
                                    byte[] keyBytes = secWebSocketKey.getBytes(StandardCharsets.UTF_8);
                                    for (byte b : keyBytes) {
                                        lbuffer[index++] = b;
                                    }
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    line2 = 4;
                                }
                                case 8 -> {
                                    lbuffer[index++] = '\r';
                                    lbuffer[index++] = '\n';
                                    lbuffer[index] = '\r';
                                    line2 = -1;
                                }
                            }
                        }

                        byte[] request = new byte[index];
                        for (int i = 0; i < index; i++) {
                            request[i] = lbuffer[i];
                        }
                        ////JNT.free(lbuffer);

                        dout.write(request);
                        dout.flush();
                        ////JNT.free(request);

                        Scanner scanner = new Scanner(din, StandardCharsets.UTF_8).useDelimiter("\\r\\n\\r\\n");
                        if (!scanner.hasNext()) {
                            line = 11;
                            linetemp = 23;
                            // #TODO write to file
                            continue;
                        }
                        String response = scanner.next();
                        //System.out.println(response);

                        channel.configureBlocking(false);

                        selector = Selector.open();
                        channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        line = linetemp;
                    }
                    case 10 -> {
                        sKey = tbuffer;
                        tbuffer = new byte[0];
                        seed ^= 0x3C6EF372FE94F82AL;
                        // String OR*_g_VN;q<0*TRAiRh0#-e@3N9LrgSCl@K1[Gh0V5.@=vvrvgHm{Xt _N+Q%h|Q
                        int a = -1787817687;
                        int k1 = a + 2094839547;
                        int b = 1079843739;
                        int k2 = b - 1093504728;
                        byte[] string = new byte[64];
                        string[0] = (byte) 204;
                        string[1] = (byte) 44;
                        string[2] = (byte) 10;
                        string[3] = (byte) 227;
                        string[4] = (byte) 157;
                        string[5] = (byte) 218;
                        string[6] = (byte) 247;
                        string[7] = (byte) 210;
                        string[8] = (byte) 157;
                        string[9] = (byte) 219;
                        string[10] = (byte) 179;
                        string[11] = (byte) 82;
                        string[12] = (byte) 252;
                        string[13] = (byte) 117;
                        string[14] = (byte) 194;
                        string[15] = (byte) 158;
                        string[16] = (byte) 197;
                        string[17] = (byte) 253;
                        string[18] = (byte) 74;
                        string[19] = (byte) 63;
                        string[20] = (byte) 87;
                        string[21] = (byte) 117;
                        string[22] = (byte) 10;
                        string[23] = (byte) 175;
                        string[24] = (byte) 4;
                        string[25] = (byte) 137;
                        string[26] = (byte) 66;
                        string[27] = (byte) 227;
                        string[28] = (byte) 138;
                        string[29] = (byte) 87;
                        string[30] = (byte) 196;
                        string[31] = (byte) 35;
                        string[32] = (byte) 233;
                        string[33] = (byte) 132;
                        string[34] = (byte) 14;
                        string[35] = (byte) 147;
                        string[36] = (byte) 142;
                        string[37] = (byte) 105;
                        string[38] = (byte) 165;
                        string[39] = (byte) 134;
                        string[40] = (byte) 37;
                        string[41] = (byte) 183;
                        string[42] = (byte) 221;
                        string[43] = (byte) 97;
                        string[44] = (byte) 24;
                        string[45] = (byte) 191;
                        string[46] = (byte) 188;
                        string[47] = (byte) 174;
                        string[48] = (byte) 52;
                        string[49] = (byte) 250;
                        string[50] = (byte) 233;
                        string[51] = (byte) 104;
                        string[52] = (byte) 247;
                        string[53] = (byte) 10;
                        string[54] = (byte) 104;
                        string[55] = (byte) 237;
                        string[56] = (byte) 73;
                        string[57] = (byte) 156;
                        string[58] = (byte) 45;
                        string[59] = (byte) 141;
                        string[60] = (byte) 144;
                        string[61] = (byte) 24;
                        string[62] = (byte) 196;
                        string[63] = (byte) 62;
                        int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                        if (n1 < 0) {
                            n1 += 256;
                        }

                        int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                        if (n2 < 0) {
                            n2 += 256;
                        }

                        for (int i = 0; i < string.length; i++) {
                            int n3 = i % 2;
                            int c = string[i] & 0xFF;
                            if (n3 == 0) {
                                int out = c - n1;
                                string[i] = (byte) out;
                                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c - n2;
                                string[i] = (byte) out;
                                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }
                        tbuffer = string;
                        seed ^= 0xF85BD804776F6A25L;
                        line = 3;
                        linetemp = 8;
                    }
                    case 11 -> { // flag
                        loggedIn = false;
                        ////JNT.free(username);
                        username = new byte[0];
                        ////JNT.free(password);
                        password = new byte[0];
                        ////JNT.free(uid);
                        uid = new byte[0];
                        ////JNT.free(sessionToken);
                        sessionToken = new byte[0];
                        value = -1;
                        ////JNT.free(cKey);
                        cKey = new byte[0];
                        ////JNT.free(sKey);
                        sKey = new byte[0];
                        byte[] flag = new byte[2];
                        flag[0] = 4;
                        flag[1] = (byte) linetemp;

                        int frameCount;
                        byte[] frame = new byte[6];

                        frame[0] = (byte) 129;

                        if (flag.length <= 125) {
                            frame[1] = (byte) flag.length;
                            frameCount = 2;
                        } else if (flag.length <= 65535) {
                            int len = flag.length;
                            frame[1] = 126;
                            frame[2] = (byte) ((len >> 8) & 0xFF);
                            frame[3] = (byte) (len & 0xFF);
                            frameCount = 4;
                        } else {
                            int len = flag.length;
                            frame[1] = 127;
                            frame[2] = (byte) ((len >> 24) & 0xFF);
                            frame[3] = (byte) ((len >> 16) & 0xFF);
                            frame[4] = (byte) ((len >> 8) & 0xFF);
                            frame[5] = (byte) (len & 0xFF);
                            frameCount = 6;
                        }

                        int bLength = frameCount + flag.length;

                        byte[] reply = new byte[bLength];

                        int bLim = 0;
                        for (int i = 0; i < frameCount; i++) {
                            reply[bLim] = frame[i];
                            bLim++;
                        }
                        ////JNT.free(frame);
                        for (byte rawDatum : flag) {
                            reply[bLim] = rawDatum;
                            bLim++;
                        }
                        ////JNT.free(flag);
                        writeBuf.put(reply);
                        ////JNT.free(reply);
                        line = 1;
                        linetemp = -1;
                    }
                    case 12 -> { // reconnect
                        if (attempt == 3 || !clientInit) {
                            Class<?> echoClass = null;
                            try {
                                echoClass = Class.forName("hack.echo.client.Echo");
                            } catch (ClassNotFoundException ignore) {}
                            if (echoClass != null) {
                                callback.apply(false, null);
                                /*Echo.authManager = null;
                                Echo.draw2D = null;
                                Echo.draw3d = null;
                                Echo.chunkManager = null;
                                Echo.handlerManager = null;
                                Echo.screenManager = null;
                                Echo.featureManager.shutdown(null);
                                Echo.featureManager = null;*/
                            }
                            System.exit(0);
                            return;
                        }
                        attempt++;

                        byte[] temp = new byte[65536];
                        int pos = 0;
                        temp[pos++] = 5;
                        temp[pos++] = (byte) ((sessionToken.length >> 8) & 0xFF);
                        temp[pos++] = (byte) (sessionToken.length & 0xFF);
                        for (byte b : sessionToken) {
                            temp[pos++] = b;
                        }

                        temp[pos++] = (byte) ((hwid.length >> 8) & 0xFF);
                        temp[pos++] = (byte) (hwid.length & 0xFF);
                        for (byte b : hwid) {
                            temp[pos++] = b;
                        }

                        wbuffer = new byte[pos];
                        for (int i = 0; i < pos; i++) {
                            wbuffer[i] = temp[i];
                        }

                        ////JNT.free(temp);
                        lastKeepAlive = time;
                        value = 0;
                        prevKeepAlive = 0;
                        seed ^= 0xC4352B7689FB920FL;
                        line = 3;
                        linetemp = 15;
                        tbuffer = cKey;
                        password = sKey;
                        cKey = new byte[0];
                        sKey = new byte[0];
                    }
                    case 13 -> { // send handshake
                        seed ^= 0x2F7AC174DC3B4A5BL;
                        // String OR*_g_VN;q<0*TRAiRh0#-e@3N9LrgSCl@K1[Gh0V5.@=vvrvgHm{Xt _N+Q%h|Q
                        int a = -757343509;
                        int k1 = a + -1391213243;
                        int b = 232925054;
                        int k2 = b - -1910251602;
                        byte[] string = new byte[64];
                        string[0] = (byte) 138;
                        string[1] = (byte) 134;
                        string[2] = (byte) 51;
                        string[3] = (byte) 43;
                        string[4] = (byte) 147;
                        string[5] = (byte) 97;
                        string[6] = (byte) 89;
                        string[7] = (byte) 166;
                        string[8] = (byte) 82;
                        string[9] = (byte) 102;
                        string[10] = (byte) 54;
                        string[11] = (byte) 7;
                        string[12] = (byte) 46;
                        string[13] = (byte) 219;
                        string[14] = (byte) 157;
                        string[15] = (byte) 162;
                        string[16] = (byte) 133;
                        string[17] = (byte) 149;
                        string[18] = (byte) 115;
                        string[19] = (byte) 113;
                        string[20] = (byte) 77;
                        string[21] = (byte) 96;
                        string[22] = (byte) 108;
                        string[23] = (byte) 253;
                        string[24] = (byte) 217;
                        string[25] = (byte) 244;
                        string[26] = (byte) 193;
                        string[27] = (byte) 176;
                        string[28] = (byte) 60;
                        string[29] = (byte) 191;
                        string[30] = (byte) 159;
                        string[31] = (byte) 33;
                        string[32] = (byte) 167;
                        string[33] = (byte) 238;
                        string[34] = (byte) 119;
                        string[35] = (byte) 95;
                        string[36] = (byte) 138;
                        string[37] = (byte) 160;
                        string[38] = (byte) 7;
                        string[39] = (byte) 182;
                        string[40] = (byte) 18;
                        string[41] = (byte) 82;
                        string[42] = (byte) 80;
                        string[43] = (byte) 22;
                        string[44] = (byte) 72;
                        string[45] = (byte) 101;
                        string[46] = (byte) 243;
                        string[47] = (byte) 116;
                        string[48] = (byte) 112;
                        string[49] = (byte) 226;
                        string[50] = (byte) 146;
                        string[51] = (byte) 34;
                        string[52] = (byte) 221;
                        string[53] = (byte) 33;
                        string[54] = (byte) 204;
                        string[55] = (byte) 63;
                        string[56] = (byte) 254;
                        string[57] = (byte) 31;
                        string[58] = (byte) 184;
                        string[59] = (byte) 26;
                        string[60] = (byte) 224;
                        string[61] = (byte) 128;
                        string[62] = (byte) 239;
                        string[63] = (byte) 34;
                        int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                        if (n1 < 0) {
                            n1 += 256;
                        }

                        int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                        if (n2 < 0) {
                            n2 += 256;
                        }

                        for (int i = 0; i < string.length; i++) {
                            int n3 = i % 2;
                            int c = string[i] & 0xFF;
                            if (n3 == 0) {
                                int out = c + n1;
                                string[i] = (byte) out;
                                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c + n2;
                                string[i] = (byte) out;
                                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }

                        //System.out.println("Initialization complete.");
                        byte[] buffer = new byte[65536];
                        buffer[0] = 0;
                        int len = string.length;
                        buffer[1] = (byte) ((len >> 8) & 0xFF);
                        buffer[2] = (byte) (len & 0xFF);
                        for (int i = 3; i < len + 3; i++) {
                            buffer[i] = string[i - 3];
                        }
                        ////JNT.free(string);

                        wbuffer = new byte[67];
                        for (int i = 0; i < 67; i++) {
                            wbuffer[i] = buffer[i];
                        }

                        //System.out.println("Writing: " + Arrays.toString(wbuffer));
                        ////JNT.free(buffer);

                        line = 2;
                        linetemp = 1;
                    }
                    case 14 -> { // post init
                        line = 9;
                        linetemp = 13;
                    }
                    case 15 -> { // post reconnect
                        line = 9;
                        linetemp = 24;
                    }
                    case 16 -> { // request classes
                        byte[] buffer = new byte[1];
                        buffer[0] = 3;
                        wbuffer = buffer;
                        line = 2;
                        linetemp = 1;
                    }
                    case 17 -> { // decrypt jar
                        byte[] out = new byte[rbuffer.length + 16];
                        for (int i = 0; i < rbuffer.length; i++) {
                            out[i] = rbuffer[i];
                        }
                        for (int i = 0; i < 16; i++) {
                            out[rbuffer.length + i] = (byte) (sKey[i] ^ 0x37);
                        }
                        callback.apply(true, out);
                        sKey = tbuffer;
                        tbuffer = new byte[0];
                        loaded = true;
                        line = 1;
                    }
                    case 18 -> {
                        byte[] copy = sKey;
                        sKey = tbuffer;
                        tbuffer = copy;
                        line = 4;
                        linetemp = 17;
                    }
                    /*case 19 -> { // request features
                        byte[] packet = new byte[1];
                        packet[0] = 9;
                        wbuffer = packet;
                        seed ^= 0x1EA61A14C4677DC3L;
                        // String net/minecraft/client/renderer/texture/TextureAtlasSprite;
                        int a = -2141389610;
                        int k1 = a + -1488686008;
                        int b = 792435363;
                        int k2 = b - 1694438925;
                        byte[] key = new byte[57];
                        key[0] = (byte) 171;
                        key[1] = (byte) 87;
                        key[2] = (byte) 162;
                        key[3] = (byte) 219;
                        key[4] = (byte) 195;
                        key[5] = (byte) 225;
                        key[6] = (byte) 214;
                        key[7] = (byte) 72;
                        key[8] = (byte) 26;
                        key[9] = (byte) 126;
                        key[10] = (byte) 45;
                        key[11] = (byte) 116;
                        key[12] = (byte) 156;
                        key[13] = (byte) 217;
                        key[14] = (byte) 10;
                        key[15] = (byte) 244;
                        key[16] = (byte) 39;
                        key[17] = (byte) 205;
                        key[18] = (byte) 206;
                        key[19] = (byte) 84;
                        key[20] = (byte) 85;
                        key[21] = (byte) 7;
                        key[22] = (byte) 5;
                        key[23] = (byte) 183;
                        key[24] = (byte) 13;
                        key[25] = (byte) 197;
                        key[26] = (byte) 59;
                        key[27] = (byte) 5;
                        key[28] = (byte) 41;
                        key[29] = (byte) 73;
                        key[30] = (byte) 109;
                        key[31] = (byte) 121;
                        key[32] = (byte) 47;
                        key[33] = (byte) 241;
                        key[34] = (byte) 231;
                        key[35] = (byte) 42;
                        key[36] = (byte) 66;
                        key[37] = (byte) 159;
                        key[38] = (byte) 213;
                        key[39] = (byte) 207;
                        key[40] = (byte) 28;
                        key[41] = (byte) 68;
                        key[42] = (byte) 129;
                        key[43] = (byte) 135;
                        key[44] = (byte) 142;
                        key[45] = (byte) 156;
                        key[46] = (byte) 108;
                        key[47] = (byte) 195;
                        key[48] = (byte) 22;
                        key[49] = (byte) 98;
                        key[50] = (byte) 220;
                        key[51] = (byte) 139;
                        key[52] = (byte) 208;
                        key[53] = (byte) 198;
                        key[54] = (byte) 82;
                        key[55] = (byte) 113;
                        key[56] = (byte) 139;
                        int n1 = ((k1 ^= k2) & 0xFF) - (int) seed;
                        if (n1 < 0) {
                            n1 += 256;
                        }

                        int n2 = ((k1 & 0xFFFF) >>> 8) - (int) seed;
                        if (n2 < 0) {
                            n2 += 256;
                        }

                        for (int i = 0; i < key.length; i++) {
                            int n3 = i % 2;
                            int c = key[i] & 0xFF;
                            if (n3 == 0) {
                                int out = c ^ n1;
                                key[i] = (byte) out;
                                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c ^ n2;
                                key[i] = (byte) out;
                                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }
                        //System.out.println("Wrong seed: " + seed);
                        seed ^= 0xDA9331624D9CEFCCL;
                        tbuffer = key;
                        line = 3;
                        linetemp = 20;
                    }*/
                    case 20 -> {
                        byte[] packet = new byte[1];
                        packet[0] = 9;
                        wbuffer = packet;
                        int a = 567711214;
                        int k3 = a - 1931556918;
                        int b = -1838589840;
                        int k4 = b - -2043509834;

                        int n3 = ((k3 ^= k4) & 0xFF) - (int) seed;
                        if (n3 < 0) {
                            n3 += 256;
                        }

                        int n4 = ((k3 & 0xFFFF) >>> 8) - (int) seed;
                        if (n4 < 0) {
                            n4 += 256;
                        }

                        //System.out.println("Post seed: " + seed);
                        //System.out.println("Pre feature key: " + new String(oKey));
                        byte[] oldKey = new byte[oKey.length];
                        for (int i = 0; i < oKey.length; i++) {
                            int n5 = i % 2;
                            int c = oKey[i] & 0xFF;
                            if (n5 == 0) {
                                int out = c + n3;
                                oldKey[i] = (byte) out;
                                n3 = ((((n3 & 0xFF) >>> 3) | ((n3 & 0xFF) << 5)) ^ out) & 0xFF;
                            } else {
                                int out = c + n4;
                                oldKey[i] = (byte) out;
                                n4 = ((((n4 & 0xFF) << 3) | ((n4 & 0xFF) >>> 5)) ^ out) & 0xFF;
                            }
                        }
                        //System.out.println("Decoded feature key: " + new String(oldKey));
                        int index = 0;
                        byte[] buffer = new byte[65536];
                        for (int i = 0; i < username.length; i++) {
                            buffer[index++] = username[i];
                        }
                        buffer[index++] = (byte) '#';
                        for (int i = 0; i < hwid.length; i++) {
                            buffer[index++] = hwid[i];
                        }
                        buffer[index++] = (byte) '#';
                        for (int i = 0; i < oldKey.length; i++) {
                            buffer[index++] = oldKey[i];
                        }
                        ////JNT.free(oldKey);
                        buffer[index++] = (byte) '#';
                        byte[] buf = new byte[21];
                        int pos = buf.length;

                        boolean negative = value < 0;
                        long v = negative ? value : -value;

                        while (v <= -10L) {
                            long q = v / 10L;
                            int digit = (int) (-(v - q * 10L));
                            buf[--pos] = (byte) ('0' + digit);
                            v = q;
                        }
                        buf[--pos] = (byte) ('0' + (int) (-v));

                        if (negative) buf[--pos] = '-';
                        for (int i = pos; i < buf.length; i++) {
                            buffer[index++] = buf[i];
                        }
                        ////JNT.free(buf);
                        byte[] key = new byte[index];
                        /*for (int i = 0; i < index; i++) {
                            key[i] = (byte) (buffer[i] ^ cKey[i % cKey.length]);
                        }*/
                        for (int i = 0; i < index; i++) {
                            key[i] = buffer[i];
                        }
                        ////JNT.free(buffer);
                        int fullGroups = key.length / 3;
                        int leftover = key.length - fullGroups * 3;
                        int outLen = (fullGroups + (leftover > 0 ? 1 : 0)) * 4;
                        byte[] out = new byte[outLen];

                        int inIndex = 0;
                        int outIndex = 0;

                        for (int g = 0; g < fullGroups; g++) {
                            int b0 = key[inIndex++] & 0xFF;
                            int b1 = key[inIndex++] & 0xFF;
                            int b2 = key[inIndex++] & 0xFF;

                            int triple = (b0 << 16) | (b1 << 8) | b2;

                            out[outIndex++] = base64_code2[(triple >> 18) & 0x3F];
                            out[outIndex++] = base64_code2[(triple >> 12) & 0x3F];
                            out[outIndex++] = base64_code2[(triple >> 6) & 0x3F];
                            out[outIndex++] = base64_code2[triple & 0x3F];
                        }

                        if (leftover == 1) {
                            int b0 = key[inIndex] & 0xFF;
                            int triple = (b0 << 16);

                            out[outIndex++] = base64_code2[(triple >> 18) & 0x3F];
                            out[outIndex++] = base64_code2[(triple >> 12) & 0x3F];
                            out[outIndex++] = (byte) '=';
                            out[outIndex] = (byte) '=';
                        } else if (leftover == 2) {
                            int b0 = key[inIndex++] & 0xFF;
                            int b1 = key[inIndex] & 0xFF;
                            int triple = (b0 << 16) | (b1 << 8);

                            out[outIndex++] = base64_code2[(triple >> 18) & 0x3F];
                            out[outIndex++] = base64_code2[(triple >> 12) & 0x3F];
                            out[outIndex++] = base64_code2[(triple >> 6) & 0x3F];
                            out[outIndex] = (byte) '=';
                        }
                        seed ^= 0xC4352B7689FB920FL;
                        tbuffer = out;
                        //System.out.println("Key: " + Arrays.toString(out) + " str: " + new String(out));
                        line = 3;
                        linetemp = 21;
                    }
                    case 21 -> {
                        seed ^= 0xC4352B7689FB920FL;
                        line = 2;
                        linetemp = 1;
                    }
                    case 22 -> {
                        sKey = tbuffer;
                        tbuffer = new byte[0];
                        //System.out.println("Server key: ");
                        for (byte b : sKey) {
                            //System.out.print(String.format("%02X ", b));
                        }
                        //System.out.println();
                        line = 1;
                    }
                    case 23 -> {
                        int inPos = 0;
                        int bitBuf = 0;
                        int bitCount = 0;

                        if (rbuffer.length >= 2) {
                            int cmf = rbuffer[0] & 0xFF;
                            int flg = rbuffer[1] & 0xFF;
                            if ((cmf & 0x0F) == 8 && (((cmf << 8) + flg) % 31) == 0) {
                                inPos = 2;
                                if ((flg & 0x20) != 0) {
                                    inPos += 4;
                                }
                            }
                        }

                        byte[] out = new byte[4096];
                        int outPos = 0;

                        final int[] LB = {
                                3, 4, 5, 6, 7, 8, 9, 10,
                                11, 13, 15, 17,
                                19, 23, 27, 31,
                                35, 43, 51, 59,
                                67, 83, 99, 115,
                                131, 163, 195, 227, 258
                        };
                        final int[] LE = {
                                0, 0, 0, 0, 0, 0, 0, 0,
                                1, 1, 1, 1,
                                2, 2, 2, 2,
                                3, 3, 3, 3,
                                4, 4, 4, 4,
                                5, 5, 5, 5, 0
                        };
                        final int[] DB = {
                                1, 2, 3, 4, 5, 7, 9, 13,
                                17, 25, 33, 49, 65, 97, 129, 193,
                                257, 385, 513, 769, 1025, 1537, 2049, 3073,
                                4097, 6145, 8193, 12289
                        };
                        final int[] DE = {
                                0, 0, 0, 0, 1, 1, 2, 2,
                                3, 3, 4, 4, 5, 5, 6, 6,
                                7, 7, 8, 8, 9, 9, 10, 10,
                                11, 11, 12, 12
                        };

                        boolean last = false;
                        while (!last) {
                            while (bitCount < 3) {
                                bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                bitCount += 8;
                            }

                            last = (bitBuf & 1) != 0;
                            int btype = (bitBuf >>> 1) & 0x3;
                            bitBuf >>>= 3;
                            bitCount -= 3;

                            if (btype == 0) {
                                int skip = bitCount % 8;
                                if (skip != 0) {
                                    bitBuf >>>= skip;
                                    bitCount -= skip;
                                }

                                int len = (rbuffer[inPos++] & 0xFF) | ((rbuffer[inPos++] & 0xFF) << 8);
                                int nlen = (rbuffer[inPos++] & 0xFF) | ((rbuffer[inPos++] & 0xFF) << 8);
                                for (int i = 0; i < len; i++) {
                                    if (outPos >= out.length) {
                                        byte[] nout = new byte[out.length * 2];
                                        for (int j = 0; j < out.length; j++) {
                                            nout[j] = out[j];
                                        }

                                        out = nout;
                                    }
                                    out[outPos++] = rbuffer[inPos++];
                                }
                                continue;
                            }

                            int numLit = 0;
                            int numDist = 0;
                            int[] litLenLen = null;
                            int[] distLen = null;

                            if (btype == 1) {
                                numLit = 288;
                                numDist = 32;
                                litLenLen = new int[numLit];
                                distLen = new int[numDist];
                                for (int i = 0; i <= 143; i++) {
                                    litLenLen[i] = 8;
                                }
                                for (int i = 144; i <= 255; i++) {
                                    litLenLen[i] = 9;
                                }
                                for (int i = 256; i <= 279; i++) {
                                    litLenLen[i] = 7;
                                }
                                for (int i = 280; i <= 287; i++) {
                                    litLenLen[i] = 8;
                                }
                                for (int i = 0; i < numDist; i++) {
                                    distLen[i] = 5;
                                }
                            } else if (btype == 2) {
                                while (bitCount < 14) {
                                    bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                    bitCount += 8;
                                }

                                int HLIT = bitBuf & 0x1F; bitBuf >>>= 5; bitCount -= 5;
                                int HDIST = bitBuf & 0x1F; bitBuf >>>= 5; bitCount -= 5;
                                int HCLEN = bitBuf & 0x0F; bitBuf >>>= 4; bitCount -= 4;
                                numLit = HLIT + 257;
                                numDist = HDIST + 1;

                                int[] clOrder = {16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15};
                                int[] clLens = new int[19];
                                for (int i = 0; i < HCLEN + 4; i++) {
                                    clLens[i] = 0;
                                }

                                for (int i = 0; i < HCLEN + 4; i++) {
                                    while (bitCount < 3) {
                                        bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                        bitCount += 8;
                                    }
                                    int val = bitBuf & 7; bitBuf >>>= 3; bitCount -= 3;
                                    clLens[clOrder[i]] = val;
                                }

                                int maxCLBits = 0;
                                for (int i = 0; i < 19; i++) if (clLens[i] > maxCLBits) maxCLBits = clLens[i];
                                int tableCL = 1 << maxCLBits;
                                int[] clTableSym = new int[tableCL];
                                int[] clTableLen = new int[tableCL];
                                for (int i = 0; i < tableCL; i++) {
                                    clTableSym[i] = -1; clTableLen[i] = 0;
                                }

                                int[] count = new int[maxCLBits + 1];
                                for (int i = 0; i < 19; i++) if (clLens[i] != 0) count[clLens[i]]++;
                                int code = 0;
                                int[] first = new int[maxCLBits + 1];
                                for (int bits = 1; bits <= maxCLBits; bits++) { code = (code + count[bits - 1]) << 1; first[bits] = code; }
                                for (int sym = 0; sym < 19; sym++) {
                                    int len = clLens[sym];
                                    if (len == 0) continue;
                                    int c = first[len]++;
                                    int rev = 0;
                                    for (int b = 0; b < len; b++) rev = (rev << 1) | ((c >>> b) & 1);
                                    int step = 1 << len;
                                    for (int i = rev; i < tableCL; i += step) { clTableSym[i] = sym; clTableLen[i] = len; }
                                }

                                int totalCodes = numLit + numDist;
                                int[] allLens = new int[totalCodes];
                                int idxAll = 0;
                                while (idxAll < totalCodes) {
                                    while (bitCount < maxCLBits) {
                                        if (inPos >= rbuffer.length) break;
                                        bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                        bitCount += 8;
                                    }

                                    int tIdx = bitBuf & ((1 << maxCLBits) - 1);
                                    int csym = clTableSym[tIdx];
                                    int clen = clTableLen[tIdx];
                                    bitBuf >>>= clen; bitCount -= clen;

                                    if (csym <= 15) {
                                        allLens[idxAll++] = csym;
                                    } else if (csym == 16) {
                                        while (bitCount < 2) {
                                            bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                            bitCount += 8;
                                        }

                                        int add = bitBuf & 3; bitBuf >>>= 2; bitCount -= 2;
                                        int repeat = 3 + add;
                                        for (int r = 0; r < repeat; r++) {
                                            allLens[idxAll] = allLens[idxAll - 1];
                                            idxAll++;
                                        }
                                    } else if (csym == 17) {
                                        while (bitCount < 3) {
                                            bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                            bitCount += 8;
                                        }

                                        int add = bitBuf & 7; bitBuf >>>= 3; bitCount -= 3;
                                        int repeat = 3 + add;
                                        for (int r = 0; r < repeat; r++) {
                                            allLens[idxAll++] = 0;
                                        }
                                    } else if (csym == 18) {
                                        while (bitCount < 7) {
                                            bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                            bitCount += 8;
                                        }

                                        int add = bitBuf & 0x7F; bitBuf >>>= 7; bitCount -= 7;
                                        int repeat = 11 + add;
                                        for (int r = 0; r < repeat; r++) {
                                            allLens[idxAll++] = 0;
                                        }
                                    }
                                }

                                litLenLen = new int[numLit];
                                distLen = new int[numDist];
                                for (int i = 0; i < numLit; i++) {
                                    litLenLen[i] = allLens[i];
                                }

                                for (int i = 0; i < numDist; i++) {
                                    distLen[i] = allLens[numLit + i];
                                }
                            }

                            int maxLitBits = 0; for (int i = 0; i < numLit; i++) if (litLenLen[i] > maxLitBits) maxLitBits = litLenLen[i];
                            int maxDistBits = 0; for (int i = 0; i < numDist; i++) if (distLen[i] > maxDistBits) maxDistBits = distLen[i];

                            int tableSizeLit = 1 << maxLitBits;
                            int[] litTableSym = new int[tableSizeLit];
                            int[] litTableLen = new int[tableSizeLit];
                            for (int i = 0; i < tableSizeLit; i++) {
                                litTableSym[i] = -1; litTableLen[i] = 0;
                            }

                            int[] count = new int[maxLitBits + 1];
                            for (int i = 0; i < numLit; i++) if (litLenLen[i] != 0) count[litLenLen[i]]++;
                            int code = 0; int[] first = new int[maxLitBits + 1];
                            for (int bits = 1; bits <= maxLitBits; bits++) {
                                code = (code + count[bits - 1]) << 1; first[bits] = code;
                            }

                            for (int sym = 0; sym < numLit; sym++) {
                                int len = litLenLen[sym]; if (len == 0) continue; int c = first[len]++;
                                int rev = 0; for (int b = 0; b < len; b++) rev = (rev << 1) | ((c >>> b) & 1);
                                int step = 1 << len; for (int i = rev; i < tableSizeLit; i += step) { litTableSym[i] = sym; litTableLen[i] = len; }
                            }

                            int tableSizeDist = 1 << Math.max(1, maxDistBits);
                            int[] distTableSym = new int[tableSizeDist];
                            int[] distTableLen = new int[tableSizeDist];
                            for (int i = 0; i < tableSizeDist; i++) {
                                distTableSym[i] = -1; distTableLen[i] = 0;
                            }

                            if (maxDistBits > 0) {
                                int[] dcount = new int[maxDistBits + 1];
                                for (int i = 0; i < numDist; i++) if (distLen[i] != 0) dcount[distLen[i]]++;
                                code = 0; first = new int[maxDistBits + 1];
                                for (int bits = 1; bits <= maxDistBits; bits++) { code = (code + dcount[bits - 1]) << 1; first[bits] = code; }
                                for (int sym = 0; sym < numDist; sym++) {
                                    int len = distLen[sym]; if (len == 0) continue; int c = first[len]++;
                                    int rev = 0; for (int b = 0; b < len; b++) rev = (rev << 1) | ((c >>> b) & 1);
                                    int step = 1 << len; for (int i = rev; i < tableSizeDist; i += step) { distTableSym[i] = sym; distTableLen[i] = len; }
                                }
                            } else {
                                distTableSym[0] = 0; distTableLen[0] = 1;
                            }

                            while (true) {
                                while (bitCount < maxLitBits) {
                                    if (inPos >= rbuffer.length) break;
                                    bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                    bitCount += 8;
                                }

                                int idxTable = bitBuf & ((1 << maxLitBits) - 1);
                                int sym = litTableSym[idxTable];
                                int lenSym = litTableLen[idxTable];
                                bitBuf >>>= lenSym; bitCount -= lenSym;

                                if (sym < 256) {
                                    if (outPos >= out.length) {
                                        byte[] nout = new byte[out.length * 2];
                                        for (int j = 0; j < out.length; j++) nout[j] = out[j];
                                        out = nout;
                                    }
                                    out[outPos++] = (byte) sym;
                                } else if (sym == 256) {
                                    break;
                                } else {
                                    int symIndex = sym - 257;
                                    int length;
                                    if (sym == 285) {
                                        length = 258;
                                    } else {
                                        int lb = LB[symIndex];
                                        int le = LE[symIndex];
                                        int extra = 0;
                                        if (le > 0) {
                                            while (bitCount < le) {
                                                bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                                bitCount += 8;
                                            }
                                            extra = bitBuf & ((1 << le) - 1);
                                            bitBuf >>>= le; bitCount -= le;
                                        }
                                        length = lb + extra;
                                    }

                                    int dLookupBits = Math.max(1, maxDistBits);
                                    while (bitCount < dLookupBits) {
                                        bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                        bitCount += 8;
                                    }

                                    int dIdx = bitBuf & ((1 << dLookupBits) - 1);
                                    int dSym = distTableSym[dIdx];
                                    int dLen = distTableLen[dIdx];
                                    bitBuf >>>= dLen; bitCount -= dLen;

                                    int distBase = DB[dSym];
                                    int distExtra = DE[dSym];
                                    int distExtraVal = 0;
                                    if (distExtra > 0) {
                                        while (bitCount < distExtra) {
                                            bitBuf |= (rbuffer[inPos++] & 0xFF) << bitCount;
                                            bitCount += 8;
                                        }
                                        distExtraVal = bitBuf & ((1 << distExtra) - 1);
                                        bitBuf >>>= distExtra; bitCount -= distExtra;
                                    }

                                    int distance = distBase + distExtraVal;
                                    while (outPos + length > out.length) {
                                        byte[] nout = new byte[out.length * 2];
                                        for (int j = 0; j < out.length; j++) {
                                            nout[j] = out[j];
                                        }

                                        out = nout;
                                    }

                                    for (int k = 0; k < length; k++) {
                                        out[outPos] = out[outPos - distance]; outPos++;
                                    }
                                }
                            }
                        }

                        byte[] result = new byte[outPos];
                        for (int i = 0; i < outPos; i++) {
                            result[i] = out[i];
                        }
                        //byte[] result = rbuffer;

                        int index = 0;
                        byte version = result[index++];
                        if (version != 1) {
                            line = 11;
                            linetemp = 30;
                            continue;
                        }
                        int length = ((result[index++] & 0xFF) << 8) | (result[index++] & 0xFF);
                        /*CentralFeatureInfoHub.globalStrings = new Concat[length];
                        for (int i = 0; i < length; i++) {
                            int strLen = ((result[index++] & 0xFF) << 8) | (result[index++] & 0xFF);
                            byte[] strBytes = new byte[strLen];
                            for (int j = 0; j < strLen; j++) {
                                strBytes[j] = result[index++];
                            }
                            int stringSeed = 0xEC40 + i * 7919;
                            CentralFeatureInfoHub.globalStrings[i] = Concat.ofEncoded(strBytes, stringSeed);
                        }

                        Echo.featureManager = new FeatureManager();
                        Echo.featureConfig = new FeatureConfig();

                        CopyOnWriteArrayList<Feature> features = new CopyOnWriteArrayList<>();
                        features.add(new TimerCooldownHudModule());
                        features.add(new WatermarkHudModule());
                        features.add(new ArrayListModule());
                        features.add(new NotificationsHudModule());

                        //Movement
                        features.add(new Sprint());
                        features.add(new NoJumpDelay());
                        features.add(new JumpReset());
                        features.add(new MoveFix());
                        features.add(new ComboHelper());
                        features.add(new GuiWalk());
                        features.add(new SnapTap());
                        features.add(new Speed());
                        features.add(new TimerExploit());
                        features.add(new AutoWalkModule());
                        features.add(new KeepSprintModule());
                        features.add(new NoSlowModule());
                        features.add(new FastStairsModule());
                        features.add(new VelocityModule());
                        features.add(new StepModule());
                        features.add(new NoWebModule());

                        //Render
                        features.add(new KeybindHudModule());
                        features.add(new ClickGUI());
                        features.add(new Nametags());
                        features.add(new AmbienceMod());
                        features.add(new PlayerESP());
                        features.add(new TargetESP());
                        features.add(new ViewModel());
                        features.add(new NoRender());
                        features.add(new XrayModule());
                        features.add(new AimVisualizerModule());
                        features.add(new FullbrightModule());
                        features.add(new StorageESPModule());
                        features.add(new SwingSpeedModule());
                        features.add(new LightESPModule());
                        features.add(new Freecam());
                        features.add(new TrajectoryRenderModule());
//        features.add(new Test());
                        features.add(new Search());

                        //Combat
                        features.add(new AimAssist());
                        features.add(new ShieldBreaker());
                        features.add(new TriggerBot());
                        features.add(new Piercing());
                        features.add(new AutoCrystal());
                        features.add(new AutoAnchor());
                        features.add(new SpearLungeModule());
                        features.add(new SpearReachModule());
                        features.add(new SpearAssistModule());
                        features.add(new MaceSwap());
                        features.add(new Prevent());
                        features.add(new Criticals());
                        features.add(new CrystalOptimizer());
                        features.add(new TotemHitModule());
                        features.add(new AutoCart());
                        features.add(new Backtrack());
                        features.add(new KnockbackDelayModule());
                        features.add(new AutoMace());

                        //Client
                        features.add(new Transactions());
                        features.add(new Teams());
                        features.add(new CapeModule());
                        features.add(new SelfDestruct());
                        features.add(new TargetControlModule());
                        features.add(new CommandLogger());
                        features.add(new VoxelEspConfig());

                        // Macros
                        features.add(new InstaCrate());
                        features.add(new KeyCharge());
                        features.add(new InstaAuctionSell());
                        features.add(new InstaSell());
                        features.add(new Tunneler());
                        features.add(new KeyPearlMacro());
                        features.add(new ChestStealer());
                        features.add(new PearlCatch());
                        features.add(new DiveBomb());

                        // Player
                        features.add(new SmartPot());
                        features.add(new PotRefill());
                        features.add(new FastEXP());
                        features.add(new ElytraSwap());
                        features.add(new TotemRefill());
                        features.add(new AutoTool());
                        features.add(new AutoTotem());
                        features.add(new AntiTrap());
                        features.add(new TotemOffhandModule());
                        features.add(new TotemDoubleHandModule());
                        features.add(new NoBreakDelay());
                        features.add(new AttributeMineModule());
                        features.add(new FastPlace());

                        // World
                        features.add(new BlockBruteforcer());

                        //Exploit
                        features.add(new HitboxMod());
                        features.add(new NoLungeCooldown());

                        features.forEach(Feature::initSettings);
                        Echo.featureManager.addFeatures(features);
                        features = Echo.featureManager.sort(features);

                        Map<Category, List<Feature>> categoryCache = new ConcurrentHashMap<>();
                        //For whatever reason during transpilation the loop breaks
                        for (Category category : Category.values()) {
                            categoryCache.put(category, new ArrayList<>());
                        }
                        for (Feature feature : features) {
                            categoryCache.get(feature.getCategory()).add(feature);
                        }
                        for (Category category : Category.values()) {
                            categoryCache.put(category, Collections.unmodifiableList(categoryCache.get(category)));
                        }
                        Echo.featureManager.addCategoryCache(categoryCache);
                        Echo.screenManager = new ScreenManager();
                        Echo.screenManager.initialize();
                        Echo.blockEspManager = new BlockEspManager();
                        Echo.spotifyManager = new SpotifyManager();*/

                        line = 1;
                    }
                    case 24 -> {
                        line = 2;
                        linetemp = 1;
                    }
                    default -> {
                        line = 11;
                        linetemp = 29;
                    }
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
            Class<?> echoClass = null;
            try {
                echoClass = Class.forName("hack.echo.client.Echo");
            } catch (ClassNotFoundException ignore) {}
            if (echoClass != null) {
                callback.apply(false, null);
                /*Echo.authManager = null;
                Echo.draw2D = null;
                Echo.draw3d = null;
                Echo.chunkManager = null;
                Echo.handlerManager = null;
                Echo.screenManager = null;
                Echo.featureManager.shutdown(null);
                Echo.featureManager = null;*/
            }
        }
        Class<?> echoClass = null;
        try {
            echoClass = Class.forName("hack.echo.client.Echo");
        } catch (ClassNotFoundException ignore) {}
        if (echoClass != null) {
            /*Echo.authManager = null;
            Echo.draw2D = null;
            Echo.draw3d = null;
            Echo.chunkManager = null;
            Echo.handlerManager = null;
            Echo.screenManager = null;
            Echo.featureManager.shutdown(null);
            Echo.featureManager = null;*/
        }
    }
}