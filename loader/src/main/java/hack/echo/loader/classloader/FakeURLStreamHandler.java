package hack.echo.loader.classloader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class FakeURLStreamHandler extends URLStreamHandler {

    private final byte[] bytes;

    public FakeURLStreamHandler(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    protected URLConnection openConnection(URL u) {
        return new FakeURLConnection(u, bytes);
    }

    public static class FakeURLConnection extends URLConnection {

        private final byte[] bytes;

        public FakeURLConnection(URL u, byte[] bytes) {
            super(u);

            this.bytes = bytes;
        }

        @Override
        public void connect() {}

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}